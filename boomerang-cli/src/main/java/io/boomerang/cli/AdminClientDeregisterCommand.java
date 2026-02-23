package io.boomerang.cli;

import io.boomerang.client.BoomerangClient;
import io.boomerang.proto.ClientDeregistrationRequest;
import io.boomerang.proto.ClientDeregistrationResponse;
import io.boomerang.proto.Status;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Subcommand for deregistering a client.
 *
 * @since 0.1.0
 */
@Command(name = "deregister", description = "Deregister a client.")
public class AdminClientDeregisterCommand extends BoomTool.BaseCommand {

  @Option(
      names = {"-i", "--id"},
      description = "The client identifier to deregister",
      required = true)
  String clientId;

  @Override
  protected Integer executeWithClient(BoomerangClient client) throws Exception {
    ClientDeregistrationRequest request =
        ClientDeregistrationRequest.newBuilder().setClientId(clientId).build();

    ClientDeregistrationResponse response = client.deregisterClient(request);

    if (response.getStatus() == Status.OK) {
      System.out.printf("Client %s deregistered successfully!%n", clientId);
      return 0;
    } else {
      System.err.printf("Error deregistering client: %s%n", response.getErrorMessage());
    }
    return 1;
  }
}
