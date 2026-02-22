package io.boomerang.cli;

import io.boomerang.cli.client.BoomerangClient;
import io.boomerang.proto.GetTaskResponse;
import io.boomerang.proto.Status;
import io.boomerang.proto.TaskDetails;
import java.nio.charset.Charset;
import java.time.Instant;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Subcommand for retrieving a specific task by ID.
 *
 * @since 0.1.0
 */
@Command(name = "get", description = "Retrieve a specific task by ID.")
public class TaskGetCommand extends BoomTool.BaseCommand {

  @Parameters(index = "0", description = "The unique identifier of the task")
  String taskId;

  @Option(
      names = {"-c", "--charset"},
      description = "Charset for payload decoding (default: ${DEFAULT-VALUE})",
      defaultValue = "UTF-8")
  String charset;

  @Override
  protected Integer executeWithClient(BoomerangClient client) throws Exception {
    GetTaskResponse response = client.getTask(taskId);

    if (response.getStatus() != Status.OK) {
      System.err.printf("Error retrieving task: %s%n", response.getErrorMessage());
      return 1;
    }

    TaskDetails task = response.getTask();
    System.out.printf("Task Details for ID: %s%n", task.getTaskId());
    System.out.println("-".repeat(50));
    System.out.printf("Client ID:       %s%n", task.getClientId());
    System.out.printf(
        "Expiration:      %s (%d ms)%n",
        Instant.ofEpochMilli(task.getExpirationMs()), task.getExpirationMs());
    System.out.printf("Recurring:       %b%n", task.getRepeatIntervalMs() > 0);
    if (task.getRepeatIntervalMs() > 0) {
      System.out.printf("Repeat Interval: %d ms%n", task.getRepeatIntervalMs());
    }

    String payloadStr = task.getPayload().toString(Charset.forName(charset));
    System.out.printf("Payload (%s): %s%n", charset, payloadStr);

    return 0;
  }
}
