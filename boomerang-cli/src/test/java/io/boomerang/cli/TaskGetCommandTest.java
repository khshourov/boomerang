package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import io.boomerang.client.BoomerangClient;
import io.boomerang.proto.GetTaskResponse;
import io.boomerang.proto.Status;
import io.boomerang.proto.TaskDetails;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

class TaskGetCommandTest {
  private BoomerangClient mockClient;
  private TaskGetCommand cmd;
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
    cmd = new TaskGetCommand();
  }

  @Test
  void shouldGetTaskSuccessfully() throws Exception {
    // Arrange
    doAnswer(
            invocation -> {
              mockClient.login(any(), any());
              return null;
            })
        .when(mockClient)
        .connect();
    TaskDetails task =
        TaskDetails.newBuilder()
            .setTaskId("task-1")
            .setClientId("client-1")
            .setExpirationMs(1000L)
            .setPayload(ByteString.copyFrom("hello", StandardCharsets.UTF_8))
            .build();
    GetTaskResponse response =
        GetTaskResponse.newBuilder().setStatus(Status.OK).setTask(task).build();
    when(mockClient.getTask(anyString())).thenReturn(response);

    IFactory factory =
        new IFactory() {
          @Override
          @SuppressWarnings("unchecked")
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == TaskGetCommand.class) {
              return (K) cmd;
            }
            return CommandLine.defaultFactory().create(cls);
          }
        };

    // Act
    int exitCode =
        new CommandLine(root, factory).execute("-u", "user", "-p", "pass", "task", "get", "task-1");

    // Assert
    assertEquals(0, exitCode);
    verify(mockClient).getTask("task-1");
  }

  @Test
  void shouldGetTaskWithCustomCharset() throws Exception {
    // Arrange
    TaskDetails task =
        TaskDetails.newBuilder()
            .setTaskId("task-1")
            .setClientId("client-1")
            .setExpirationMs(1000L)
            .setPayload(ByteString.copyFrom("hello", StandardCharsets.UTF_16))
            .build();
    GetTaskResponse response =
        GetTaskResponse.newBuilder().setStatus(Status.OK).setTask(task).build();
    when(mockClient.getTask(anyString())).thenReturn(response);

    IFactory factory =
        new IFactory() {
          @Override
          @SuppressWarnings("unchecked")
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == TaskGetCommand.class) {
              return (K) cmd;
            }
            return CommandLine.defaultFactory().create(cls);
          }
        };

    // Act
    int exitCode =
        new CommandLine(root, factory)
            .execute("-u", "user", "-p", "pass", "task", "get", "task-1", "-c", "UTF-16");

    // Assert
    assertEquals(0, exitCode);
    verify(mockClient).getTask("task-1");
  }

  @Test
  void shouldHandleGetTaskFailure() throws Exception {
    // Arrange
    GetTaskResponse response =
        GetTaskResponse.newBuilder().setStatus(Status.ERROR).setErrorMessage("Not found").build();
    when(mockClient.getTask(anyString())).thenReturn(response);

    IFactory factory =
        new IFactory() {
          @Override
          @SuppressWarnings("unchecked")
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == TaskGetCommand.class) {
              return (K) cmd;
            }
            return CommandLine.defaultFactory().create(cls);
          }
        };

    // Act
    int exitCode =
        new CommandLine(root, factory).execute("-u", "user", "-p", "pass", "task", "get", "task-1");

    // Assert
    assertEquals(1, exitCode);
  }
}
