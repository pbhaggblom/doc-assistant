package command;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(name = "doc",
        subcommands = {AssistantCommand.class, StatusCommand.class, IngestionCommand.class, StopCommand.class, ResetCommand.class},
        mixinStandardHelpOptions = true,
        description = "Assistant that answers questions about Kubernetes")
public class EntryPoint {
}
