package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.boomerang.client.BoomerangClient;
import io.boomerang.proto.RegistrationResponse;
import io.boomerang.proto.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

class TaskRegisterCommandTest {
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
  void shouldRegisterTaskSuccessfully() throws Exception {
    // Arrange
    doAnswer(
            invocation -> {
              mockClient.login(any(), any());
              return null;
            })
        .when(mockClient)
        .connect();
    RegistrationResponse response =
        RegistrationResponse.newBuilder()
            .setStatus(Status.OK)
            .setTaskId("test-task-id")
            .setScheduledTimeMs(1000L)
            .build();
    when(mockClient.register(any())).thenReturn(response);

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
            .execute("-u", "user", "-p", "pass", "task", "register", "-l", "hello", "-d", "5000");

    // Assert
    assertEquals(0, exitCode);
    verify(mockClient).connect();
    verify(mockClient).login(any(), any());
    verify(mockClient).register(any());
  }

  @Test
  void shouldHandleRegistrationFailure() throws Exception {
    // Arrange
    RegistrationResponse response =
        RegistrationResponse.newBuilder()
            .setStatus(Status.ERROR)
            .setErrorMessage("Something went wrong")
            .build();
    when(mockClient.register(any())).thenReturn(response);

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
            .execute("-u", "user", "-p", "pass", "task", "register", "-l", "hello", "-d", "5000");

    // Assert
    assertEquals(1, exitCode);
    verify(mockClient).register(any());
  }

  @Test
  void shouldRegisterTaskWithCustomCharset() throws Exception {
    // Arrange
    RegistrationResponse response =
        RegistrationResponse.newBuilder()
            .setStatus(Status.OK)
            .setTaskId("test-task-id")
            .setScheduledTimeMs(1000L)
            .build();
    when(mockClient.register(any())).thenReturn(response);

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
                "-u",
                "user",
                "-p",
                "pass",
                "task",
                "register",
                "-l",
                "hello",
                "-d",
                "5000",
                "-c",
                "UTF-16");

    // Assert
    assertEquals(0, exitCode);
    verify(mockClient).register(any());
  }
}
