package command;

import auth.AuthService;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.inject.Inject;
import org.pbhaggblom.documentation.*;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "stop", description = "Stop ingestion. Requires admin access.")
public class StopCommand implements Callable<Integer> {

    @GrpcClient("ingestor")
    DocumentationServiceGrpc.DocumentationServiceBlockingStub documentService;

    @Inject
    AuthService authService;

    @Override
    public Integer call() throws Exception {
        try {
            Metadata headers = authService.getAuthHeaders();

            var authenticatedStub = documentService.withInterceptors(
                    MetadataUtils.newAttachHeadersInterceptor(headers)
            );

            StopRequest request = StopRequest.newBuilder().build();
            StopResponse response = authenticatedStub.stopIngestion(request);

            System.out.println(response.getResponse());

            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
