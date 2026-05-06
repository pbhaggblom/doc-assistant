import io.quarkus.grpc.GrpcClient;
import org.pbhaggblom.documentation.DocumentationServiceGrpc.DocumentationServiceBlockingStub;
import org.pbhaggblom.documentation.IngestionRequest;
import org.pbhaggblom.documentation.IngestionResponse;
import picocli.CommandLine.Command;

import java.util.Iterator;
import java.util.concurrent.Callable;

@Command(name = "ingest")
public class IngestionCommand implements Callable<Integer> {

    @GrpcClient("ingestor")
    DocumentationServiceBlockingStub documentService;

    @Override
    public Integer call() {
        try {
            IngestionRequest request = IngestionRequest.newBuilder().build();
            Iterator<IngestionResponse> responses = documentService.startIngestion(request);

            while (responses.hasNext()) {
                IngestionResponse response = responses.next();
                System.out.println(response.getResponse());
                System.out.flush();
            }
            System.out.println("Ingestion completed successfully");
            return 0;

        } catch (Exception e) {
            System.err.println("Error during ingestion: " + e.getMessage());
            return 1;
        }
    }
}
