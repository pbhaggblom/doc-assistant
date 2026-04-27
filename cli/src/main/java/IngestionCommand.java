import io.quarkus.grpc.GrpcClient;
import org.pbhaggblom.*;
import picocli.CommandLine.Command;

import java.util.Iterator;
import java.util.concurrent.Callable;

@Command(name = "ingest")
public class IngestionCommand implements Callable<Integer> {

    @GrpcClient("ingestor")
    DocumentationServiceGrpc.DocumentationServiceBlockingStub documentService;

    @Override
    public Integer call() {
        try {
            IngestionRequest request = IngestionRequest.newBuilder().build();
            Iterator<IngestionResponse> responses = documentService.startIngestion(request);

            while (responses.hasNext()) {
                IngestionResponse response = responses.next();
                System.out.print(response.getResponse());
                System.out.flush();
            }
            System.out.println();
            return 0;

        } catch (Exception e) {
            System.err.println("Error during stream: " + e.getMessage());
            return 1;
        }
    }
}
