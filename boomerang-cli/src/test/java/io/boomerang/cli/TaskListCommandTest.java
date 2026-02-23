package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.boomerang.client.BoomerangClient;
import io.boomerang.proto.ListTasksRequest;
import io.boomerang.proto.ListTasksResponse;
import io.boomerang.proto.Status;
import io.boomerang.proto.TaskDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

class TaskListCommandTest {
  private BoomerangClient mockClient;
  private TaskListCommand cmd;
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
    cmd = new TaskListCommand();
  }

  @Test
  void shouldListTasksSuccessfully() throws Exception {
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
            .build();
    ListTasksResponse response =
        ListTasksResponse.newBuilder().setStatus(Status.OK).addTasks(task).build();
    when(mockClient.listTasks(any(ListTasksRequest.class))).thenReturn(response);

    IFactory factory =
        new IFactory() {
          @Override
          @SuppressWarnings("unchecked")
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == TaskListCommand.class) {
              return (K) cmd;
            }
            return CommandLine.defaultFactory().create(cls);
          }
        };

    // Act
    int exitCode =
        new CommandLine(root, factory)
            .execute("-u", "user", "-p", "pass", "task", "list", "--limit", "10");

    // Assert
    assertEquals(0, exitCode);
    verify(mockClient).listTasks(any(ListTasksRequest.class));
  }

  @Test
  void shouldListTasksWithFilters() throws Exception {
    // Arrange
    ListTasksResponse response = ListTasksResponse.newBuilder().setStatus(Status.OK).build();
    when(mockClient.listTasks(any(ListTasksRequest.class))).thenReturn(response);

    IFactory factory =
        new IFactory() {
          @Override
          @SuppressWarnings("unchecked")
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == TaskListCommand.class) {
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
                "list",
                "--client-id",
                "other-client",
                "--after",
                "5m",
                "--before",
                "1h",
                "--recurring",
                "true");

    // Assert
    assertEquals(0, exitCode);
    verify(mockClient).listTasks(any(ListTasksRequest.class));
  }

  @Test
  void shouldHandleListTasksFailure() throws Exception {
    // Arrange
    ListTasksResponse response =
        ListTasksResponse.newBuilder()
            .setStatus(Status.ERROR)
            .setErrorMessage("List failed")
            .build();
    when(mockClient.listTasks(any(ListTasksRequest.class))).thenReturn(response);

    IFactory factory =
        new IFactory() {
          @Override
          @SuppressWarnings("unchecked")
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == TaskListCommand.class) {
              return (K) cmd;
            }
            return CommandLine.defaultFactory().create(cls);
          }
        };

    // Act
    int exitCode =
        new CommandLine(root, factory).execute("-u", "user", "-p", "pass", "task", "list");

    // Assert
    assertEquals(1, exitCode);
  }
}
