import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class ChromaIndexLoader {

    @ConfigProperty(name = "quarkus.langchain4j.chroma.url")
    String chromaBaseUrl;

    @ConfigProperty(name = "quarkus.langchain4j.chroma.collection-name")
    String collectionName;

    @ConfigProperty(name = "chroma.tenant")
    String chromaTenant;

    @ConfigProperty(name = "chroma.database")
    String chromaDb;

    private static final int BATCH_SIZE = 1000;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Set<String> loadExistingIndex() throws Exception {
        String collectionId = getCollectionId();
        return fetchAllMetadata(collectionId);
    }

    private String getCollectionId() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(chromaBaseUrl + "/api/v2/tenants/" + chromaTenant + "/databases/" + chromaDb + "/collections/" + collectionName))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch collection '" + collectionName + "': HTTP "
                    + response.statusCode() + " - " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        return json.get("id").asText();
    }

    private Set<String> fetchAllMetadata(String collectionId) throws Exception {
        Set<String> cache = new HashSet<>();
        int offset = 0;

        while (true) {
            String body = String.format("""
                    {
                      "limit": %d,
                      "offset": %d,
                      "include": ["metadatas"]
                    }
                    """, BATCH_SIZE, offset);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chromaBaseUrl + "/api/v2/tenants/" + chromaTenant + "/databases/" + chromaDb + "/collections/" + collectionId + "/get"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode metadatas = json.get("metadatas");

            if (metadatas == null || metadatas.isEmpty()) break;

            for (JsonNode metadata : metadatas) {
                String fileName = getTextOrNull(metadata, "file_name");
                String absoluteDirPath = getTextOrNull(metadata, "absolute_directory_path");
                String hash = getTextOrNull(metadata, "file_hash");

                if (fileName != null && hash != null) {
                    String key = fileName + "|" + absoluteDirPath + "|" + hash;
                    cache.add(key);
                }
            }

            if (metadatas.size() < BATCH_SIZE) break;

            offset += BATCH_SIZE;
            System.out.printf("Loaded %d records...%n", offset);
        }

        return cache;
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asText() : null;
    }
}