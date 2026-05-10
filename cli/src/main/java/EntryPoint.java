import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(name = "doc", subcommands = {AssistantCommand.class, IngestionCommand.class, StatusCommand.class})
public class EntryPoint {
}
