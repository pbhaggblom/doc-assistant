import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

@RegisterAiService
public interface DocAssistant {

    @SystemMessage("""
        You are a technical assistant specialized in finding information in documentation.
        Your replies are concise and accurate.
        ONLY give answers based on the context provided to you. Do NOT use your external knowledge.
        If you can't find the answer in the documentation provided to you, just reply: "Couldn't find an answer in the documentation", nothing else.
        """)
    Multi<String> searchDocs(String query);
}
