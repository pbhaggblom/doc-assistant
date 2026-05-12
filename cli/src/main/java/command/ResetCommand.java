package command;

import auth.AuthService;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.inject.Inject;
import org.pbhaggblom.documentation.DocumentationServiceGrpc.DocumentationServiceBlockingStub;
import org.pbhaggblom.documentation.ResetRequest;
import org.pbhaggblom.documentation.ResetResponse;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "reset", description = "Resets vector database. Requires admin access.")
public class ResetCommand implements Callable<Integer> {

    @GrpcClient("ingestor")
    DocumentationServiceBlockingStub documentService;

    @Inject
    AuthService authService;

    @Override
    public Integer call() throws Exception {
        try {
            Metadata headers = authService.getAuthHeaders();

            var authenticatedStub = documentService.withInterceptors(
                    MetadataUtils.newAttachHeadersInterceptor(headers)
            );

            ResetRequest request = ResetRequest.newBuilder().build();
            ResetResponse response = authenticatedStub.clearDatabase(request);
            System.out.println(response.getResponse());
            System.out.flush();
            return 0;
        } catch (Exception e) {
            System.err.println("Error while resetting database: " + e.getMessage());
            return 1;
        }
    }
}
