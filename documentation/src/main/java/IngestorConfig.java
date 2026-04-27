import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.Produces;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

@Dependent
public class IngestorConfig {

    @Produces
    @ApplicationScoped // Skapas en gång och delas av alla
    public EmbeddingStoreIngestor ingestor(ChromaEmbeddingStore store, EmbeddingModel model) {
        return EmbeddingStoreIngestor.builder()
                .embeddingStore(store)
                .embeddingModel(model)
                .documentSplitter(recursive(500, 75))
                .build();
    }
}
