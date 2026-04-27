import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;

@Path("/doc")
public class DocumentationResource {

    @Inject
    ChromaEmbeddingStore store;

    @Inject
    DocumentIngestionService ingestor;

    @Path("/ingest")
    public void ingest() {
        ingestor.ingest();
    }
}
