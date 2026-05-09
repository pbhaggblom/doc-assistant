import io.quarkus.grpc.GrpcClient;
import org.pbhaggblom.documentation.DocumentationServiceGrpc.DocumentationServiceBlockingStub;
import org.pbhaggblom.documentation.StatusRequest;
import org.pbhaggblom.documentation.StatusResponse;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "update")
public class UpdateCommand implements Callable<Integer> {

    @GrpcClient("ingestor")
    DocumentationServiceBlockingStub documentService;

    @Override
    public Integer call() {
        try {
            StatusRequest request = StatusRequest.newBuilder().build();
            StatusResponse response = documentService.checkStatus(request);
            System.out.print(response.getResponse());
            System.out.flush();
            return 0;
        } catch (Exception e) {
            System.err.println("Error while checking status: " + e.getMessage());
            return 1;
        }
    }
}
