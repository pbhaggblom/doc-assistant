import io.quarkus.grpc.GrpcClient;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import org.pbhaggblom.AssistantServiceGrpc;
import org.pbhaggblom.DocRequest;
import org.pbhaggblom.DocResponse;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Iterator;
import java.util.concurrent.Callable;

@TopCommand
@Command(name = "k?s")
public class AssistantCommand implements Callable<Integer> {

    @GrpcClient("assistant")
    AssistantServiceGrpc.AssistantServiceBlockingStub assistantService;

    @Parameters(index = "0", description = "User question", defaultValue = "")
    private String question;

    @Override
    public Integer call() throws InterruptedException {

        if (question == null || question.isBlank()) {
            System.err.println("Question cannot be empty.");
            return 1;
        }

        DocRequest query = DocRequest.newBuilder()
                .setQuestion(question)
                .build();

        try {
            Iterator<DocResponse> responses = assistantService.resultStream(query);

            while (responses.hasNext()) {
                DocResponse response = responses.next();
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

    public static void main(String... args) {
        int exitCode = new CommandLine(new AssistantCommand()).execute(args);
        System.exit(exitCode);
    }
}
