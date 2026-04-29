import io.quarkus.grpc.GrpcClient;
import org.pbhaggblom.assistant.AssistantServiceGrpc;
import org.pbhaggblom.assistant.AssistantRequest;
import org.pbhaggblom.assistant.AssistantResponse;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Iterator;
import java.util.concurrent.Callable;

@Command(name = "ask")
public class AssistantCommand implements Callable<Integer> {

    @GrpcClient("assistant")
    AssistantServiceGrpc.AssistantServiceBlockingStub assistantService;

    @Parameters(index = "0", description = "User question", defaultValue = "")
    private String question;

    @Override
    public Integer call() {

        if (question == null || question.isBlank()) {
            System.err.println("Question cannot be empty.");
            return 1;
        }

        AssistantRequest query = AssistantRequest.newBuilder()
                .setQuestion(question)
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
