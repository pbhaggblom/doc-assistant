import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import jakarta.inject.Inject;
import org.pbhaggblom.documentation.DocumentationServiceGrpc.DocumentationServiceBlockingStub;
import org.pbhaggblom.documentation.IngestionRequest;
import org.pbhaggblom.documentation.IngestionResponse;
import picocli.CommandLine.Command;

import java.io.Console;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "ingest", description = "Initialize ingestion of the documentation. Requires admin access.")
public class IngestionCommand implements Callable<Integer> {

    @GrpcClient("ingestor")
    DocumentationServiceBlockingStub documentService;

    @Inject
    OidcClient oidcClient;

    @Override
    public Integer call() {
        try {
            Console console = System.console();
            if (console == null) {
                System.err.println("No console available.");
                return 1;
            }

            String username = console.readLine("Username: ");
            char[] password = console.readPassword("Password: ");

            Map<String, String> grantOptions = new HashMap<>();
            grantOptions.put("username", username);
            grantOptions.put("password", new String(password));

            Optional<Tokens> tokens = oidcClient.getTokens(grantOptions).await().asOptional().indefinitely();
            Arrays.fill(password, ' ');

            if (tokens.isEmpty()) {
                System.err.println("Authorization failed.");
                return 1;
            }

            Metadata headers = new Metadata();
            Metadata.Key<String> authKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
            headers.put(authKey, "Bearer " + tokens.get().getAccessToken());

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
