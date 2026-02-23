package io.boomerang.cli;

import io.boomerang.cli.client.BoomerangClient;
import io.boomerang.proto.ListTasksRequest;
import io.boomerang.proto.ListTasksResponse;
import io.boomerang.proto.Status;
import io.boomerang.proto.TaskDetails;
import java.time.Instant;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Subcommand for listing tasks with filtering and pagination.
 *
 * @since 0.1.0
 */
@Command(name = "list", description = "List tasks with filtering and pagination.")
public class TaskListCommand extends BoomTool.BaseCommand {

  @Option(
      names = {"--client-id"},
      description = "Filter by client (Admin only)")
  String clientId;

  @Option(
      names = {"--after"},
      description = "Filter tasks scheduled after this interval from now (e.g., 5m, 1h)")
  String after;

  @Option(
      names = {"--before"},
      description = "Filter tasks scheduled before this interval from now (e.g., 2h, 1d)")
  String before;

  @Option(
      names = {"--recurring"},
      description = "Filter for recurring tasks (true/false)",
      arity = "1")
  Boolean recurring;

  @Option(
      names = {"-l", "--limit"},
      description = "Maximum number of tasks to display per page (default: ${DEFAULT-VALUE})",
      defaultValue = "20")
  int limit;

  @Option(
      names = {"--all"},
      description = "Fetch all pages automatically")
  boolean all;

  @Option(
      names = {"--next-token"},
      description = "Pagination token for the next page")
  String nextTokenOption;

  @Override
  protected Integer executeWithClient(BoomerangClient client) throws Exception {
    String nextToken = nextTokenOption != null ? nextTokenOption : "";
    boolean hasMore = true;

    System.out.printf(
        "%-36s | %-15s | %-20s | %-10s%n", "Task ID", "Client ID", "Expiration (ms)", "Recurring");
    System.out.println("-".repeat(90));

    while (hasMore) {
      ListTasksRequest.Builder requestBuilder =
          ListTasksRequest.newBuilder().setLimit(limit).setNextToken(nextToken);

      if (clientId != null) {
        requestBuilder.setClientId(clientId);
      }
      if (after != null) {
        requestBuilder.setScheduledAfter(parseIntervalToMs(after));
      }
      if (before != null) {
        requestBuilder.setScheduledBefore(parseIntervalToMs(before));
      }
      if (recurring != null) {
        requestBuilder.setIsRecurring(recurring);
      }

      ListTasksResponse response = client.listTasks(requestBuilder.build());

      if (response.getStatus() != Status.OK) {
        System.err.printf("Error listing tasks: %s%n", response.getErrorMessage());
        return 1;
      }

      for (TaskDetails task : response.getTasksList()) {
        System.out.printf(
            "%-36s | %-15s | %-20d | %-10b%n",
            task.getTaskId(),
            task.getClientId(),
            task.getExpirationMs(),
            task.getRepeatIntervalMs() > 0);
      }

      nextToken = response.getNextToken();
      if (!nextToken.isEmpty() && !all) {
        System.out.printf(
            "--- More tasks available. Use --next-token %s to see more. ---%n", nextToken);
      }
      hasMore = all && !nextToken.isEmpty();
    }

    return 0;
  }
}
