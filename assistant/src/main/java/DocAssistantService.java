import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.pbhaggblom.assistant.AssistantRequest;
import org.pbhaggblom.assistant.AssistantResponse;
import org.pbhaggblom.assistant.AssistantService;

@GrpcService
public class DocAssistantService implements AssistantService {

    @Inject
    DocAssistant assistant;

    @Inject
    ContextRetriever contextRetriever;

    @Override
    public Multi<AssistantResponse> resultStream(AssistantRequest request) {
        return contextRetriever.retrieve(request.getQuestion())
                .onItem().transformToMulti(context ->
                        assistant.searchDocs(request.getQuestion(), context)
                )
                .onItem().transform(chunk ->
                        AssistantResponse.newBuilder().setTextChunk(chunk).build()
                );
    }
}
