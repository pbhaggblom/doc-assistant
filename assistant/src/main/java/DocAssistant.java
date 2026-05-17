import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

@RegisterAiService
public interface DocAssistant {

    @SystemMessage("""
            You are a documentation assistant. Use ONLY the provided documentation to answer.
            If an answer is not found in the documentation, inform the user that you couldn't find an answer in the documentation.
            """)
    @UserMessage("""
            Documentation: {docContext}
            ---
            Question: {question}
            """)
    Multi<String> searchDocs(String question, String docContext);
}
