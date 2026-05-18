import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.stream.Collectors;

@ApplicationScoped
public class ContextRetriever {

    @Inject
    ChromaEmbeddingStore store;

    @Inject
    EmbeddingModel embeddingModel;

    public Uni<String> retrieve(String query) {
        return Uni.createFrom().item(() -> {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            EmbeddingSearchResult<TextSegment> results = store.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(3)
                            .minScore(0.7)
                            .build()
            );
            return results.matches().stream()
                    .map(m -> m.embedded().text())
                    .collect(Collectors.joining("\n---\n"));
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
