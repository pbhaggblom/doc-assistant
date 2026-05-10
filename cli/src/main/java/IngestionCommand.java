import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.inject.Inject;
import org.pbhaggblom.documentation.DocumentationServiceGrpc.DocumentationServiceBlockingStub;
import org.pbhaggblom.documentation.IngestionRequest;
import org.pbhaggblom.documentation.IngestionResponse;
import picocli.CommandLine.Command;

import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "ingest", description = "Initialize ingestion of the documentation. Requires admin access.")
public class IngestionCommand implements Callable<Integer> {

    @GrpcClient("ingestor")
    DocumentationServiceBlockingStub documentService;

    @Inject
    AuthService authService;

    @Override
    public Integer call() {
        try {
            Metadata headers = authService.getAuthHeaders();

            var authenticatedStub = documentService.withInterceptors(
                    MetadataUtils.newAttachHeadersInterceptor(headers)
            );

            IngestionRequest request = IngestionRequest.newBuilder().build();
            Iterator<IngestionResponse> responses = authenticatedStub.startIngestion(request);

            while (responses.hasNext()) {
                IngestionResponse response = responses.next();
                System.out.println(response.getResponse());
            }
            System.out.println("Ingestion completed successfully");
            return 0;

        } catch (Exception e) {
            System.err.println("Error during ingestion: " + e.getMessage());
            return 1;
        }
    }
}
