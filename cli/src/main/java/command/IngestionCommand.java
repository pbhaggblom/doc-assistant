package command;

import auth.AuthService;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Command(name = "ingest", description = "Initialize ingestion of the documentation. Requires admin access.")
public class IngestionCommand implements Callable<Integer> {

    @GrpcClient("ingestor")
    DocumentationServiceBlockingStub documentService;

    @Inject
    AuthService authService;

    private final AtomicBoolean success = new AtomicBoolean(false);

    @Override
    public Integer call() {

        success.set(false);
        addShutdownHook();

        try {
            Metadata headers = authService.getAuthHeaders();

            System.out.println("Ingestion initialized...");

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
            success.set(true);
            return 0;

        } catch (Exception e) {

            if (isShutdown(e)) {
                return 1; 
            }

            success.set(true);
            System.err.println("Error during ingestion: " + e.getMessage());
            return 1;
        }
    }

    private void addShutdownHook() {
        Thread mainThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (mainThread.isAlive() && !success.get()) {
                System.err.println("\nStream interrupted. Ingestion running in the background on server");
            }
        }));
    }

    private boolean isShutdown(Throwable t) {
        String msg = t.getMessage();
        return msg != null && (msg.contains("UNAVAILABLE") || msg.contains("shutdownNow"));
    }
}
