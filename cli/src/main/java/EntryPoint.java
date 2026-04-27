import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(name = "k?s", subcommands = {AssistantCommand.class, IngestionCommand.class})
public class EntryPoint {
}
