package io.boomerang.cli;

import io.boomerang.client.BoomerangClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Subcommand for canceling an existing task.
 *
 * @since 0.1.0
 */
@Command(name = "cancel", description = "Cancel an existing task.")
public class TaskCancelCommand extends BoomTool.BaseCommand {

  @Option(
      names = {"-t", "--task-id"},
      description = "The UUID of the task to cancel",
      required = true)
  String taskId;

  @Override
  protected Integer executeWithClient(BoomerangClient client) throws Exception {
    if (client.cancel(taskId)) {
      System.out.printf("Task %s canceled successfully!%n", taskId);
      return 0;
    } else {
      System.err.printf("Failed to cancel task %s.%n", taskId);
      return 1;
    }
  }
}
