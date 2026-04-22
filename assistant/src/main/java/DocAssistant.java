import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

@RegisterAiService
public interface DocAssistant {

    @SystemMessage("""
        You are an assistant specialized in retrieving information from Kubernetes documentation.
        If you can't find the answer in the documentation, say so.
        """)
    Multi<String> searchDocs(String query);
}
