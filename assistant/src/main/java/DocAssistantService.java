import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.pbhaggblom.DocRequest;
import org.pbhaggblom.DocResponse;
import org.pbhaggblom.AssistantService;

@GrpcService
public class DocAssistantService implements AssistantService {

    @Inject
    DocAssistant assistant;

    @Override
    public Multi<DocResponse> resultStream(DocRequest request) {
        return assistant.searchDocs(request.getQuestion()).onItem()
                .transform(chunk -> DocResponse.newBuilder().setTextChunk(chunk).build());
    }
}
