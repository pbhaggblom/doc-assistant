package command;

import io.quarkus.grpc.GrpcClient;
import org.pbhaggblom.documentation.DocumentationServiceGrpc.DocumentationServiceBlockingStub;
import org.pbhaggblom.documentation.StatusRequest;
import org.pbhaggblom.documentation.StatusResponse;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "status", description = "Check if there has been any changes in the documentation since last ingestion")
public class StatusCommand implements Callable<Integer> {

    @GrpcClient("ingestor")
    DocumentationServiceBlockingStub documentService;

    @Override
    public Integer call() {
        try {
            System.out.println("Checking status...");
            StatusRequest request = StatusRequest.newBuilder().build();
            StatusResponse response = documentService.checkStatus(request);
            System.out.print(response.getResponse());
            return 0;
        } catch (Exception e) {
            System.err.println("Error while checking status: " + e.getMessage());
            return 1;
        }
    }
}
