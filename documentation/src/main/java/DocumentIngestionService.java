import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;


@ApplicationScoped
public class DocumentIngestionService {

    @Inject
    ChromaEmbeddingStore store;

    @Inject
    EmbeddingModel embeddingModel;

    public void ingest(@Observes StartupEvent ev,
                       @ConfigProperty(name = "rag.location") Path path) {
        store.removeAll();

        PathMatcher matcher = p -> p.getFileName().toString().endsWith(".md");
        List<Document> list = FileSystemDocumentLoader.loadDocumentsRecursively(path, matcher);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .documentSplitter(recursive(500, 75))
                .build();
        ingestor.ingest(list);
        Log.info("Documents ingested successfully");
    }
}