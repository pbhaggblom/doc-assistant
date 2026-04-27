import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class DocumentIngestionService {

    @Inject
    ChromaEmbeddingStore store;

    @Inject
    EmbeddingModel embeddingModel;

    @ConfigProperty(name = "rag.location")
    Path path;

    private static final float[] ZERO_VECTOR = new float[768];
    private static final Embedding DUMMY_EMBEDDING = Embedding.from(ZERO_VECTOR);

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public void ingest() {
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Ingestion is already running");
        }

        PathMatcher matcher = p -> p.getFileName().toString().endsWith(".md");
        List<Document> list = loadDocuments(path, matcher);

        for (Document doc : list) {
            String fileName = doc.metadata().getString("file_name");
            String currentHash = calculateHash(doc.text());

            if (isAlreadyIndexed(fileName, currentHash)) {
                Log.info("Skipping " + fileName + " - no changes detected.");
                continue;
            }

            Log.info("Changes detected in " + fileName + ". Updating index...");

            store.removeAll(metadataKey("file_name").isEqualTo(fileName));

            doc.metadata().put("file_hash", currentHash);
            doc.metadata().put("last_updated", LocalDateTime.now().toString());

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(store)
                    .embeddingModel(embeddingModel)
                    .documentSplitter(recursive(500, 75))
                    .build();
            ingestor.ingest(doc);

        }
        Log.info("Documents ingested successfully");
    }

    private List<Document> loadDocuments(Path path, PathMatcher matcher) {
        List<Document> list = FileSystemDocumentLoader.loadDocumentsRecursively(path, matcher);
        return list.stream().peek(doc -> doc.metadata().put("ingestion_date", LocalDate.now().toString())).toList();
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

}