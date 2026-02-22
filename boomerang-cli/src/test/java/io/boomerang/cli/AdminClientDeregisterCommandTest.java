package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.boomerang.cli.client.BoomerangClient;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.ClientDeregistrationResponse;
import io.boomerang.proto.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

class AdminClientDeregisterCommandTest {
  private BoomerangClient mockClient;
  private AdminClientDeregisterCommand cmd;
  private BoomTool root;

  @BeforeEach
  void setUp() {
    mockClient = mock(BoomerangClient.class);
    cmd =
        new AdminClientDeregisterCommand() {
          @Override
          protected BoomerangClient createClient(String host, int port) {
            return mockClient;
          }
        };
    root = new BoomTool();
  }

  @Test
  void shouldDeregisterClientSuccessfully() throws Exception {
    // Arrange
    when(mockClient.login(any(), any())).thenReturn(true);
    ClientDeregistrationResponse response =
        ClientDeregistrationResponse.newBuilder().setStatus(Status.OK).build();
    when(mockClient.sendRequest(any(BoomerangEnvelope.class)))
        .thenReturn(
            BoomerangEnvelope.newBuilder().setClientDeregistrationResponse(response).build());

    IFactory factory =
        new IFactory() {
          @Override
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == AdminClientDeregisterCommand.class) {
              return (K) cmd;
            }
            return CommandLine.defaultFactory().create(cls);
          }
        };

    // Act
    int exitCode =
        new CommandLine(root, factory)
            .execute(
                "--user",
                "admin-user",
                "--password",
                "admin123",
                "admin",
                "deregister",
                "-i",
                "target-client");

    // Assert
    assertEquals(0, exitCode);
    verify(mockClient).connect();
    verify(mockClient).login(any(), any());
    verify(mockClient).sendRequest(any(BoomerangEnvelope.class));
  }
}
