import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.pbhaggblom.documentation.*;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;


@GrpcService
public class DocumentIngestionService implements DocumentationService {

    @Inject
    ChromaEmbeddingStore store;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStoreIngestor ingestor;

    @ConfigProperty(name = "rag.location")
    Path path;

    private static final float[] ZERO_VECTOR = new float[1024];
    private static final Embedding DUMMY_EMBEDDING = Embedding.from(ZERO_VECTOR);

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    @Override
    public Uni<StatusResponse> checkStatus(StatusRequest request) {
        return Uni.createFrom().item(() -> {
                    PathMatcher matcher = getPathMatcher();
                    return loadDocuments(path, matcher);
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .map(docs -> {
                    List<String> changedFiles = docs.stream()
                            .filter(this::hasPendingChanges)
                            .map(doc -> doc.metadata().getString("file_name"))
                            .toList();

                    String response = changedFiles.isEmpty()
                            ? "No documents have been updated\n"
                            : "Following documents have been updated: \n\n" + listFiles(changedFiles);

                    return StatusResponse.newBuilder()
                            .setResponse(response)
                            .build();
                });
    }

    @Override
    @RolesAllowed("admin")
    public Multi<IngestionResponse> startIngestion(IngestionRequest request) {
        return Multi.createFrom().emitter(emitter -> {
            if (!isRunning.compareAndSet(false, true)) {
                emitter.fail(Status.ALREADY_EXISTS.withDescription("Ingestion is already running").asRuntimeException());
                return;
            }

            stopRequested.set(false);

            Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    PathMatcher matcher = getPathMatcher();
                    List<Document> docs = loadDocuments(path, matcher);

                    for (Document doc : docs) {
                        if (stopRequested.get()) {
                            System.out.println("Ingestion stop requested.");
                            break;
                        }

                        String response = processDocument(doc);

                        if (!emitter.isCancelled()) {
                            emitter.emit(IngestionResponse.newBuilder().setResponse(response).build());
                        }
                    }
                    emitter.complete();

                } catch (Exception e) {
                    emitter.fail(e);
                } finally {
                    isRunning.set(false);
                    stopRequested.set(false);
                }
            });
        });
    }

    @Override
    @RolesAllowed("admin")
    public Uni<ResetResponse> clearDatabase(ResetRequest request) {
        return Uni.createFrom().item(() -> {
                    store.removeAll(metadataKey("file_name").isNotEqualTo(""));
                    return "Database cleared successfully.";
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .map(message -> ResetResponse.newBuilder().setResponse(message).build())
                .onFailure().transform(e ->
                    Status.INTERNAL
                            .withDescription("Failed to clear database: " + e.getMessage())
                            .asRuntimeException()
                );
    }

    @Override
    @RolesAllowed("admin")
    public Uni<StopResponse> stopIngestion(StopRequest request) {
        return Uni.createFrom().item(() -> {
                    if (isRunning.get()) {
                        stopRequested.set(true);
                        return "Ingestion stop requested";
                    }
                    return "Ingestion is not running";
                })
                .map(message -> StopResponse.newBuilder().setResponse(message).build());
    }

    private List<Document> loadDocuments(Path path, PathMatcher matcher) {
        return FileSystemDocumentLoader.loadDocumentsRecursively(path, matcher);
    }

    private String processDocument(Document doc) {
        String fileName = doc.metadata().getString("file_name");
        String currentHash = calculateHash(doc.text());
        String absoluteDirPath = doc.metadata().getString("absolute_directory_path");

        if (isAlreadyIndexed(fileName, currentHash, absoluteDirPath)) {
            return "Skipping " + fileName + " - no changes detected.";
        }

        String res = "Changes detected in " + fileName + ". Updating index...";

        doc.metadata().put("file_hash", currentHash);

        String cleanedText = cleanMarkdown(doc.text());
        String textWithContext = "Document: " + fileName + "\n\n" + cleanedText;
        Document cleanDoc = Document.from(textWithContext, doc.metadata());

        try {
            store.removeAll(metadataKey("file_name").isEqualTo(fileName)
                    .and(metadataKey("absolute_directory_path").isEqualTo(absoluteDirPath))
                    .and(metadataKey("file_hash").isNotEqualTo(currentHash)));
            ingestor.ingest(cleanDoc);
        } catch (Exception e) {
            store.removeAll(metadataKey("file_name").isEqualTo(fileName)
                    .and(metadataKey("absolute_directory_path").isEqualTo(absoluteDirPath))
                    .and(metadataKey("file_hash").isEqualTo(currentHash)));
            throw Status.INTERNAL
                    .withDescription("Ingestion error: " + e.getMessage())
                    .asRuntimeException();
        }
        return res;
    }

    private boolean isAlreadyIndexed(String fileName, String hash, String absoluteDirPath) {
        Filter filter = metadataKey("file_name").isEqualTo(fileName)
                .and(metadataKey("absolute_directory_path").isEqualTo(absoluteDirPath))
                .and(metadataKey("file_hash").isEqualTo(hash));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(DUMMY_EMBEDDING)
                .filter(filter)
                .maxResults(1)
                .build();

        return !store.search(request).matches().isEmpty();
    }

    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't calculate hash", e);
        }
    }

    private String cleanMarkdown(String content) {
        if (content == null) return "";

        String cleaned = content;

        cleaned = cleaned.replaceAll("(?s)^---.*?---", "");
        cleaned = cleaned.replaceAll("\\{\\{<\\s*glossary_tooltip\\s+text=\"([^\"]+)\"[^>]*>\\}\\}", "$1");
        cleaned = cleaned.replaceAll("\\{\\{<.*?>\\}\\}", "");
        cleaned = cleaned.replaceAll("(?i)<!--.*?-->", "");
        cleaned = cleaned.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");

        return cleaned.trim();
    }

    private String listFiles(List<String> list) {
        StringBuilder sb = new StringBuilder();

        for (String fileName : list) {
            sb.append(fileName).append("\n");
        }
        return sb.toString();
    }

    private PathMatcher getPathMatcher() {
        return p -> {
            String fullPath = p.toString();
            String fileName = p.getFileName().toString();

            if (fullPath.contains("contribute/") ||
                    fullPath.contains("doc-contributor-tools/") ||
                    fullPath.contains("home/") ||
                    fullPath.contains("images/")) {
                return false;
            }

            return fileName.endsWith(".md") &&
                    !fileName.startsWith("_") &&
                    !fileName.startsWith("test") &&
                    !fileName.startsWith("index") &&
                    !fileName.startsWith("README");
        };
    }

    private boolean hasPendingChanges(Document doc) {
        String fileName = doc.metadata().getString("file_name");
        String currentHash = calculateHash(doc.text());
        String absoluteDirPath = doc.metadata().getString("absolute_directory_path");
        return !isAlreadyIndexed(fileName, currentHash, absoluteDirPath);
    }
}