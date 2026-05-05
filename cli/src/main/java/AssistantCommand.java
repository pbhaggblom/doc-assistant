import io.quarkus.grpc.GrpcClient;
import org.pbhaggblom.assistant.AssistantServiceGrpc;
import org.pbhaggblom.assistant.AssistantRequest;
import org.pbhaggblom.assistant.AssistantResponse;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "ask")
public class AssistantCommand implements Callable<Integer> {

    @GrpcClient("assistant")
    AssistantServiceGrpc.AssistantServiceBlockingStub assistantService;

    @Parameters(index = "0..*", description = "User question", defaultValue = "")
    private List<String> questionWords;

    @Override
    public Integer call() {

        String fullQuestion = String.join(" ", questionWords);

        if (fullQuestion.isBlank()) {
            System.err.println("Please provide a question.");
            return 1;
        }

        AssistantRequest query = AssistantRequest.newBuilder()
                .setQuestion(fullQuestion)
                .build();

        try {
            Iterator<AssistantResponse> responses = assistantService.resultStream(query);

            while (responses.hasNext()) {
                AssistantResponse response = responses.next();
                System.out.print(response.getTextChunk());
                System.out.flush();
            }
            System.out.println();
        } catch (Exception e) {
            System.err.println("Error during stream: " + e.getMessage());
            return 1;
        }

        return 0;
    }

}
