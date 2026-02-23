package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.boomerang.client.BoomerangClient;
import io.boomerang.client.BoomerangException;
import io.boomerang.proto.Status;
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
    root =
        new BoomTool() {
          @Override
          protected BoomerangClient createClient(
              String host, int port, String clientId, String password) {
            return mockClient;
          }
        };
    cmd = new TaskRegisterCommand();
  }

  @Test
  void shouldReturnOneOnAuthFailure() throws Exception {
    // Arrange
    doAnswer(
            invocation -> {
              mockClient.login(any(), any());
              return null;
            })
        .when(mockClient)
        .connect();
    doThrow(new BoomerangException(Status.UNAUTHORIZED, "Invalid credentials"))
        .when(mockClient)
        .login(any(), any());

    IFactory factory =
        new IFactory() {
          @Override
          @SuppressWarnings("unchecked")
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
    doAnswer(
            invocation -> {
              mockClient.login(any(), any());
              return null;
            })
        .when(mockClient)
        .connect();
    doThrow(new BoomerangException("Connection error")).when(mockClient).login(any(), any());

    IFactory factory =
        new IFactory() {
          @Override
          @SuppressWarnings("unchecked")
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
