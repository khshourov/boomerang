package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.boomerang.client.BoomerangClient;
import io.boomerang.proto.ClientRegistrationResponse;
import io.boomerang.proto.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

class AdminClientRegisterCommandTest {
  private BoomerangClient mockClient;
  private AdminClientRegisterCommand cmd;
  private BoomTool root;

  @BeforeEach
  void setUp() {
    mockClient = mock(BoomerangClient.class);
    root =
        new BoomTool() {
          @Override
          protected BoomerangClient createClient(
              String host, int port, String clientId, String password) {
            return mockClient;
          }
        };
    cmd = new AdminClientRegisterCommand();
  }

  @Test
  void shouldRegisterClientSuccessfully() throws Exception {
    // Arrange
    doAnswer(
            invocation -> {
              mockClient.login(any(), any());
              return null;
            })
        .when(mockClient)
        .connect();
    ClientRegistrationResponse response =
        ClientRegistrationResponse.newBuilder().setStatus(Status.OK).build();
    when(mockClient.registerClient(any())).thenReturn(response);

    IFactory factory =
        new IFactory() {
          @Override
          @SuppressWarnings("unchecked")
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == AdminClientRegisterCommand.class) {
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
                "register",
                "-i",
                "new-client",
                "-W",
                "new-pass",
                "--cb-endpoint",
                "localhost:8081",
                "--dlq-destination",
                "dlq-topic");

    // Assert
    assertEquals(0, exitCode);
    verify(mockClient).connect();
    verify(mockClient).login(any(), any());
    verify(mockClient).registerClient(any());
  }
}
