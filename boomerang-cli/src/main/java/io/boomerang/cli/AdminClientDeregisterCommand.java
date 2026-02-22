package io.boomerang.cli;

import io.boomerang.cli.client.BoomerangClient;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.ClientDeregistrationRequest;
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
    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder().setClientDeregistration(request).build();

    BoomerangEnvelope response = client.sendRequest(envelope);

    if (response.hasClientDeregistrationResponse()) {
      var deregistration = response.getClientDeregistrationResponse();
      if (deregistration.getStatus() == Status.OK) {
        System.out.printf("Client %s deregistered successfully!%n", clientId);
        return 0;
      } else {
        System.err.printf("Error deregistering client: %s%n", deregistration.getErrorMessage());
      }
    } else {
      System.err.println("Error: Unexpected response from server.");
    }
    return 1;
  }
}
