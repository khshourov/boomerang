package io.boomerang.cli;

import io.boomerang.client.BoomerangClient;
import io.boomerang.proto.RegistrationResponse;
import io.boomerang.proto.Status;
import io.boomerang.proto.Task;
import java.nio.charset.Charset;
import java.time.Instant;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Subcommand for registering a new task.
 *
 * @since 0.1.0
 */
@Command(name = "register", description = "Register a new task.")
public class TaskRegisterCommand extends BoomTool.BaseCommand {

  @Option(
      names = {"-l", "--payload"},
      description = "The binary payload for the task (as a string)",
      required = true)
  String payload;

  @Option(
      names = {"-d", "--delay"},
      description = "Delay from registration (e.g., 5000, 5m, 1h)",
      required = true)
  String delay;

  @Option(
      names = {"-r", "--repeat"},
      description = "Repeat interval (e.g., 10000, 10s, 1m). 0 for no repetition",
      defaultValue = "0")
  String repeat;

  @Option(
      names = {"-c", "--charset"},
      description = "Charset for payload encoding (default: ${DEFAULT-VALUE})",
      defaultValue = "UTF-8")
  String charset;

  @Override
  protected Integer executeWithClient(BoomerangClient client) throws Exception {
    long delayMs = parseIntervalToMs(delay);
    long repeatIntervalMs = parseIntervalToMs(repeat);

    Task task =
        Task.newBuilder()
            .setPayload(com.google.protobuf.ByteString.copyFrom(payload, Charset.forName(charset)))
            .setDelayMs(delayMs)
            .setRepeatIntervalMs(repeatIntervalMs)
            .build();

    RegistrationResponse response = client.register(task);

    if (response.getStatus() == Status.OK) {
      System.out.printf("Task registered successfully! ID: %s%n", response.getTaskId());
      System.out.printf(
          "Scheduled at:    %s (%d ms)%n",
          Instant.ofEpochMilli(response.getScheduledTimeMs()), response.getScheduledTimeMs());
      return 0;
    } else {
      System.err.printf("Error registering task: %s%n", response.getErrorMessage());
    }
    return 1;
  }
}
