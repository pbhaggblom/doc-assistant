import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.pbhaggblom.documentation.*;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;


@GrpcService
@Singleton
public class DocumentIngestionService implements DocumentationService {

    @Inject
    ChromaEmbeddingStore store;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStoreIngestor ingestor;

    @ConfigProperty(name = "rag.location")
    Path path;

    private static final float[] ZERO_VECTOR = new float[768];
    private static final Embedding DUMMY_EMBEDDING = Embedding.from(ZERO_VECTOR);

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Blocking
    @Override
    public Uni<StatusResponse> checkStatus(StatusRequest request) {
        PathMatcher matcher = getPathMatcher();

        List<String> changedFiles = loadDocuments(path, matcher).stream()
                .filter(this::hasPendingChanges)
                .map(doc -> doc.metadata().getString("file_name"))
                .toList();

        String response = changedFiles.isEmpty()
                ? "No documents have been updated since last ingestion"
                : "Following documents have been updated since last ingestion: \n\n" + listFiles(changedFiles);

        return Uni.createFrom()
                .item(StatusResponse
                .newBuilder()
                .setResponse(response)
                .build());
    }

    @Blocking
    @Override
    @RolesAllowed("admin")
    public Multi<IngestionResponse> startIngestion(IngestionRequest request) {
        if (!isRunning.compareAndSet(false, true)) {
            throw Status.ALREADY_EXISTS
                    .withDescription("Ingestion is already running")
                    .asRuntimeException();
        }

        PathMatcher matcher = getPathMatcher();

        return Multi.createFrom().iterable(loadDocuments(path, matcher))
                .onItem().transform(Unchecked.function(doc -> {

                    String fileName = doc.metadata().getString("file_name");
                    String currentHash = calculateHash(doc.text());

                    if (isAlreadyIndexed(fileName, currentHash)) {
                        String res = "Skipping " + fileName + " - no changes detected.";
                        return IngestionResponse.newBuilder().setResponse(res).build();
                    }

                    String res = "Changes detected in " + fileName + ". Updating index...";

                    doc.metadata().put("file_hash", currentHash);
                    doc.metadata().put("ingestion_date", LocalDateTime.now().toString());

                    String cleanedText = cleanMarkdown(doc.text());
                    String textWithContext = "Document: " + fileName + "\n\n" + cleanedText;
                    Document cleanDoc = Document.from(textWithContext, doc.metadata());

                    try {
                        ingestor.ingest(cleanDoc);
                        store.removeAll(metadataKey("file_name").isEqualTo(fileName)
                                .and(metadataKey("file_hash").isNotEqualTo(currentHash)));
                    } catch (Exception e) {
                        store.removeAll(metadataKey("file_name").isEqualTo(fileName)
                                        .and(metadataKey("file_hash").isEqualTo(currentHash)));
                        throw e;
                    }

                    return IngestionResponse.newBuilder().setResponse(res).build();
                }))
                .onTermination().invoke(() -> isRunning.set(false));
    }

    private List<Document> loadDocuments(Path path, PathMatcher matcher) {
        return FileSystemDocumentLoader.loadDocumentsRecursively(path, matcher);
    }

    private boolean isAlreadyIndexed(String fileName, String hash) {
        Filter filter = metadataKey("file_name").isEqualTo(fileName)
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
        return p -> p.getFileName().toString().endsWith(".md") &&
                !p.getFileName().toString().startsWith("_");
    }

    private boolean hasPendingChanges(Document doc) {
        String fileName = doc.metadata().getString("file_name");
        String currentHash = calculateHash(doc.text());
        return !isAlreadyIndexed(fileName, currentHash);
    }
}