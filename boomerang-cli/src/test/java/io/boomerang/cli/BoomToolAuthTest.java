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

class BoomToolAuthTest {
  private BoomerangClient mockClient;
  private TaskRegisterCommand cmd;
  private BoomTool root;

  @BeforeEach
  void setUp() {
    mockClient = mock(BoomerangClient.class);
    cmd =
        new TaskRegisterCommand() {
          @Override
          protected BoomerangClient createClient(String host, int port) {
            return mockClient;
          }
        };
    root = new BoomTool();
  }

  @Test
  void shouldReturnOneOnAuthFailure() throws Exception {
    // Arrange
    when(mockClient.login(any(), any())).thenReturn(false);

    IFactory factory =
        new IFactory() {
          @Override
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == TaskRegisterCommand.class) {
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
                "user",
                "--password",
                "pass",
                "task",
                "register",
                "-l",
                "hello",
                "-d",
                "5000");

    // Assert
    assertEquals(1, exitCode);
  }

  @Test
  void shouldReturnOneOnConnectionError() throws Exception {
    // Arrange
    when(mockClient.login(any(), any())).thenThrow(new RuntimeException("Connection error"));

    IFactory factory =
        new IFactory() {
          @Override
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == TaskRegisterCommand.class) {
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
                "user",
                "--password",
                "pass",
                "task",
                "register",
                "-l",
                "hello",
                "-d",
                "5000");

    // Assert
    assertEquals(1, exitCode);
  }
}
