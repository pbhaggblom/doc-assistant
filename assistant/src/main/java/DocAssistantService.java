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

    @Override
    public Multi<AssistantResponse> resultStream(AssistantRequest request) {
        return assistant.searchDocs(request.getQuestion()).onItem()
                .transform(chunk -> AssistantResponse.newBuilder().setTextChunk(chunk).build());
    }
}
