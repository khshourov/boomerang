package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.boomerang.cli.client.BoomerangClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

class AdminClientRegisterCommandErrorTest {
  private BoomerangClient mockClient;
  private AdminClientRegisterCommand cmd;
  private BoomTool root;

  @BeforeEach
  void setUp() {
    mockClient = mock(BoomerangClient.class);
    cmd =
        new AdminClientRegisterCommand() {
          @Override
          protected BoomerangClient createClient(String host, int port) {
            return mockClient;
          }
        };
    root = new BoomTool();
  }

  @Test
  void shouldReturnOneOnInvalidProtocol() throws Exception {
    // Arrange
    when(mockClient.login(any(), any())).thenReturn(true);

    IFactory factory =
        new IFactory() {
          @Override
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
                "--cb-protocol",
                "INVALID",
                "--cb-endpoint",
                "localhost:8081",
                "--dlq-destination",
                "dlq-topic");

    // Assert
    assertEquals(1, exitCode);
  }
}
