package auth;

import io.grpc.Metadata;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.Console;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AuthService {

    @Inject
    OidcClient oidcClient;

    public Metadata getAuthHeaders() {
        Console console = System.console();
        if (console == null) {
            System.err.println("No console available.");
        }

        String username = console.readLine("Username: ");
        char[] password = console.readPassword("Password: ");

        Map<String, String> grantOptions = new HashMap<>();
        grantOptions.put("username", username);
        grantOptions.put("password", new String(password));

        Optional<Tokens> tokens = oidcClient.getTokens(grantOptions).await().asOptional().indefinitely();
        Arrays.fill(password, ' ');

        if (tokens.isEmpty()) {
            throw new RuntimeException("Authorization failed. Check your credentials.");
        }

        Metadata headers = new Metadata();
        Metadata.Key<String> authKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(authKey, "Bearer " + tokens.get().getAccessToken());

        return headers;
    }
}
