package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import io.boomerang.client.BoomerangClient;
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
  void shouldReturnOneOnInvalidProtocol() throws Exception {
    // Act
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
                "--cb-protocol",
                "INVALID",
                "--cb-endpoint",
                "localhost:8081",
                "--dlq-destination",
                "dlq-topic");

    // Assert
    assertEquals(2, exitCode);
  }
}
