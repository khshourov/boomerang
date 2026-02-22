package io.boomerang.cli;

import io.boomerang.cli.client.BoomerangClient;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CancellationRequest;
import io.boomerang.proto.Status;
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
    CancellationRequest request = CancellationRequest.newBuilder().setTaskId(taskId).build();
    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder().setCancellationRequest(request).build();

    BoomerangEnvelope response = client.sendRequest(envelope);

    if (response.hasCancellationResponse()) {
      var cancellation = response.getCancellationResponse();
      if (cancellation.getStatus() == Status.OK) {
        System.out.printf("Task %s canceled successfully!%n", taskId);
        return 0;
      } else {
        System.err.printf("Error canceling task: %s%n", cancellation.getErrorMessage());
      }
    } else {
      System.err.println("Error: Unexpected response from server.");
    }
    return 1;
  }
}
