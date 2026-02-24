package io.boomerang.web.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.boomerang.client.BoomerangClient;
import io.boomerang.client.BoomerangException;
import io.boomerang.proto.GetTaskResponse;
import io.boomerang.proto.ListTasksResponse;
import io.boomerang.proto.RegistrationResponse;
import io.boomerang.proto.Status;
import io.boomerang.proto.TaskDetails;
import io.boomerang.web.api.dto.TaskRequest;
import io.boomerang.web.api.dto.TaskResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

  @Mock private BoomerangClientProvider clientProvider;
  @Mock private BoomerangClient client;

  @InjectMocks private TaskService taskService;

  @Test
  void testRegisterSuccess() {
    String sessionId = "session-123";
    String payload = Base64.getEncoder().encodeToString("data".getBytes());
    TaskRequest request = new TaskRequest(payload, 1000L, 0L);

    when(clientProvider.createClient(sessionId)).thenReturn(client);
    when(client.register(any()))
        .thenReturn(
            RegistrationResponse.newBuilder()
                .setTaskId("task-123")
                .setStatus(Status.OK)
                .setScheduledTimeMs(5000L)
                .build());

    TaskResponse response = taskService.register(sessionId, request);

    assertThat(response.taskId()).isEqualTo("task-123");
    assertThat(response.status()).isEqualTo("OK");
    verify(client).connect();
  }

  @Test
  void testCancelSuccess() {
    String sessionId = "session-123";
    String taskId = "task-123";

    when(clientProvider.createClient(sessionId)).thenReturn(client);
    when(client.cancel(taskId)).thenReturn(true);

    boolean result = taskService.cancel(sessionId, taskId);

    assertThat(result).isTrue();
    verify(client).connect();
  }

  @Test
  void testGetTaskSuccess() {
    String sessionId = "session-123";
    String taskId = "task-123";

    when(clientProvider.createClient(sessionId)).thenReturn(client);
    when(client.getTask(taskId))
        .thenReturn(
            GetTaskResponse.newBuilder()
                .setStatus(Status.OK)
                .setTask(TaskDetails.newBuilder().setTaskId(taskId).setClientId("client-1").build())
                .build());

    io.boomerang.web.api.dto.GetTaskResponse response = taskService.getTask(sessionId, taskId);

    assertThat(response.status()).isEqualTo("OK");
    assertThat(response.task().taskId()).isEqualTo(taskId);
  }

  @Test
  void testListTasksSuccess() {
    String sessionId = "session-123";

    when(clientProvider.createClient(sessionId)).thenReturn(client);
    when(client.listTasks(any()))
        .thenReturn(
            ListTasksResponse.newBuilder()
                .setStatus(Status.OK)
                .addTasks(TaskDetails.newBuilder().setTaskId("task-1").build())
                .build());

    io.boomerang.web.api.dto.ListTasksResponse response =
        taskService.listTasks(sessionId, null, null, null, null, null);

    assertThat(response.status()).isEqualTo("OK");
    assertThat(response.tasks()).hasSize(1);
  }

  @Test
  void testGetTaskFailure() {
    String sessionId = "session-123";
    String taskId = "task-123";

    when(clientProvider.createClient(sessionId)).thenReturn(client);
    when(client.getTask(taskId)).thenThrow(new BoomerangException("Task not found"));

    assertThatThrownBy(() -> taskService.getTask(sessionId, taskId))
        .isInstanceOf(BoomerangException.class)
        .hasMessageContaining("Task not found");
  }

  @Test
  void testListTasksFailure() {
    String sessionId = "session-123";

    when(clientProvider.createClient(sessionId)).thenReturn(client);
    when(client.listTasks(any())).thenThrow(new BoomerangException("Listing failed"));

    assertThatThrownBy(() -> taskService.listTasks(sessionId, null, null, null, null, null))
        .isInstanceOf(BoomerangException.class)
        .hasMessageContaining("Listing failed");
  }
}
