package io.boomerang.cli;

import io.boomerang.cli.client.BoomerangClient;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CallbackConfig;
import io.boomerang.proto.ClientRegistrationRequest;
import io.boomerang.proto.DLQPolicy;
import io.boomerang.proto.RetryPolicy;
import io.boomerang.proto.Status;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Subcommand for registering a new client.
 *
 * @since 0.1.0
 */
@Command(name = "register", description = "Register a new client.")
public class AdminClientRegisterCommand extends BoomTool.BaseCommand {

  @Option(
      names = {"-i", "--id"},
      description = "The unique identifier for the client",
      required = true)
  String clientId;

  @Option(
      names = {"-W", "--client-password"},
      description = "The password for the new client",
      required = true)
  char[] clientPassword;

  @Option(
      names = {"-a", "--admin"},
      description = "Flag to indicate if the client is an admin",
      defaultValue = "false")
  boolean isAdmin;

  // Callback options
  @Option(
      names = {"--cb-protocol"},
      description = "Callback protocol (TCP, GRPC, HTTP, UDP)",
      defaultValue = "TCP")
  String callbackProtocol;

  @Option(
      names = {"--cb-endpoint"},
      description = "Callback endpoint (e.g., localhost:8080 or http://example.com/webhook)",
      required = true)
  String callbackEndpoint;

  // Retry options
  @Option(
      names = {"--retry-max"},
      description = "Maximum number of retry attempts",
      defaultValue = "3")
  int retryMaxAttempts;

  @Option(
      names = {"--retry-strategy"},
      description = "Retry strategy (FIXED, EXPONENTIAL)",
      defaultValue = "FIXED")
  String retryStrategy;

  @Option(
      names = {"--retry-interval"},
      description = "Retry interval in milliseconds",
      defaultValue = "1000")
  long retryIntervalMs;

  @Option(
      names = {"--retry-max-interval"},
      description = "Maximum retry interval in milliseconds (for exponential backoff)",
      defaultValue = "60000")
  long retryMaxIntervalMs;

  // DLQ options
  @Option(
      names = {"--dlq-destination"},
      description = "Destination for dead letter queue (e.g., dlq-client-id or endpoint)",
      required = true)
  String dlqDestination;

  @Override
  protected Integer executeWithClient(BoomerangClient client) throws Exception {
    try {
      CallbackConfig callback =
          CallbackConfig.newBuilder()
              .setProtocol(CallbackConfig.Protocol.valueOf(callbackProtocol.toUpperCase()))
              .setEndpoint(callbackEndpoint)
              .build();

      RetryPolicy retry =
          RetryPolicy.newBuilder()
              .setMaxAttempts(retryMaxAttempts)
              .setStrategy(RetryPolicy.BackoffStrategy.valueOf(retryStrategy.toUpperCase()))
              .setIntervalMs(retryIntervalMs)
              .setMaxIntervalMs(retryMaxIntervalMs)
              .build();

      DLQPolicy dlq = DLQPolicy.newBuilder().setDestination(dlqDestination).build();

      ClientRegistrationRequest request =
          ClientRegistrationRequest.newBuilder()
              .setClientId(clientId)
              .setPassword(new String(clientPassword))
              .setIsAdmin(isAdmin)
              .setCallback(callback)
              .setRetry(retry)
              .setDlq(dlq)
              .build();

      BoomerangEnvelope envelope =
          BoomerangEnvelope.newBuilder().setClientRegistration(request).build();

      BoomerangEnvelope response = client.sendRequest(envelope);

      if (response.hasClientRegistrationResponse()) {
        var registration = response.getClientRegistrationResponse();
        if (registration.getStatus() == Status.OK) {
          System.out.printf("Client %s registered successfully!%n", clientId);
          return 0;
        } else {
          System.err.printf("Error registering client: %s%n", registration.getErrorMessage());
        }
      } else {
        System.err.println("Error: Unexpected response from server.");
      }
      return 1;
    } finally {
      if (clientPassword != null) {
        java.util.Arrays.fill(clientPassword, '\0');
      }
    }
  }
}
