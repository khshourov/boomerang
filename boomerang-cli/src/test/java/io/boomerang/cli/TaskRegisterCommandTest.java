package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.boomerang.cli.client.BoomerangClient;
import io.boomerang.proto.BoomerangEnvelope;
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
  void shouldRegisterTaskSuccessfully() throws Exception {
    // Arrange
    when(mockClient.login(any(), any())).thenReturn(true);
    RegistrationResponse response =
        RegistrationResponse.newBuilder()
            .setStatus(Status.OK)
            .setTaskId("test-task-id")
            .setScheduledTimeMs(1000L)
            .build();
    when(mockClient.sendRequest(any(BoomerangEnvelope.class)))
        .thenReturn(BoomerangEnvelope.newBuilder().setRegistrationResponse(response).build());

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
            .execute("-u", "user", "-p", "pass", "task", "register", "-l", "hello", "-d", "5000");

    // Assert
    assertEquals(0, exitCode);
    verify(mockClient).connect();
    verify(mockClient).login(any(), any());
    verify(mockClient).sendRequest(any(BoomerangEnvelope.class));
  }

  @Test
  void shouldHandleRegistrationFailure() throws Exception {
    // Arrange
    when(mockClient.login(any(), any())).thenReturn(true);
    RegistrationResponse response =
        RegistrationResponse.newBuilder()
            .setStatus(Status.ERROR)
            .setErrorMessage("Something went wrong")
            .build();
    when(mockClient.sendRequest(any(BoomerangEnvelope.class)))
        .thenReturn(BoomerangEnvelope.newBuilder().setRegistrationResponse(response).build());

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
            .execute("-u", "user", "-p", "pass", "task", "register", "-l", "hello", "-d", "5000");

    // Assert
    assertEquals(1, exitCode);
    verify(mockClient).sendRequest(any(BoomerangEnvelope.class));
  }

  @Test
  void shouldRegisterTaskWithCustomCharset() throws Exception {
    // Arrange
    when(mockClient.login(any(), any())).thenReturn(true);
    RegistrationResponse response =
        RegistrationResponse.newBuilder()
            .setStatus(Status.OK)
            .setTaskId("test-task-id")
            .setScheduledTimeMs(1000L)
            .build();
    when(mockClient.sendRequest(any(BoomerangEnvelope.class)))
        .thenReturn(BoomerangEnvelope.newBuilder().setRegistrationResponse(response).build());

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
    verify(mockClient).sendRequest(any(BoomerangEnvelope.class));
  }

  @Test
  void shouldHandleUnexpectedResponse() throws Exception {
    // Arrange
    when(mockClient.login(any(), any())).thenReturn(true);
    // Return an envelope without a registration response
    when(mockClient.sendRequest(any(BoomerangEnvelope.class)))
        .thenReturn(BoomerangEnvelope.newBuilder().setSessionId("some-id").build());

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
            .execute("-u", "user", "-p", "pass", "task", "register", "-l", "hello", "-d", "5000");

    // Assert
    assertEquals(1, exitCode);
    verify(mockClient).sendRequest(any(BoomerangEnvelope.class));
  }
}
