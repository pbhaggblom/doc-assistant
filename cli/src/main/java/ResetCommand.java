import io.quarkus.grpc.GrpcClient;
import org.pbhaggblom.documentation.DocumentationServiceGrpc.DocumentationServiceBlockingStub;
import org.pbhaggblom.documentation.ResetRequest;
import org.pbhaggblom.documentation.ResetResponse;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "reset-db", description = "Resets vector database. Requires admin access.")
public class ResetCommand implements Callable<Integer> {

    @GrpcClient("ingestor")
    DocumentationServiceBlockingStub documentService;

    @Override
    public Integer call() throws Exception {
        try {
            ResetRequest request = ResetRequest.newBuilder().build();
            ResetResponse response = documentService.clearDatabase(request);
            System.out.println(response.getResponse());
            System.out.flush();
            return 0;
        } catch (Exception e) {
            System.err.println("Error while resetting database: " + e.getMessage());
            return 1;
        }
    }
}
