package io.boomerang.web.api.service;

import com.google.protobuf.ByteString;
import io.boomerang.client.BoomerangClient;
import io.boomerang.client.BoomerangException;
import io.boomerang.proto.ListTasksRequest;
import io.boomerang.proto.Task;
import io.boomerang.web.api.dto.GetTaskResponse;
import io.boomerang.web.api.dto.ListTasksResponse;
import io.boomerang.web.api.dto.TaskDetails;
import io.boomerang.web.api.dto.TaskRequest;
import io.boomerang.web.api.dto.TaskResponse;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for managing Boomerang tasks.
 *
 * <p>This service interacts with the Boomerang core server using a session-aware client.
 */
@Service
public class TaskService {

  @Autowired private BoomerangClientProvider clientProvider;

  /**
   * Registers a new task.
   *
   * @param sessionId the session ID
   * @param taskDto the {@link TaskRequest} with task details
   * @return the {@link TaskResponse} from the server
   */
  public TaskResponse register(String sessionId, TaskRequest taskDto) {
    try (BoomerangClient client = getClient(sessionId)) {
      Task.Builder taskBuilder = Task.newBuilder();
      if (taskDto.payload() != null) {
        taskBuilder.setPayload(ByteString.copyFrom(Base64.getDecoder().decode(taskDto.payload())));
      }
      taskBuilder.setDelayMs(taskDto.delayMs());
      taskBuilder.setRepeatIntervalMs(taskDto.repeatIntervalMs());

      io.boomerang.proto.RegistrationResponse response = client.register(taskBuilder.build());
      return new TaskResponse(
          response.getTaskId(),
          response.getStatus().name(),
          response.getScheduledTimeMs(),
          response.getErrorMessage());
    }
  }

  /**
   * Cancels a task.
   *
   * @param sessionId the session ID
   * @param taskId the task ID to cancel
   * @return {@code true} if successful, {@code false} otherwise
   */
  public boolean cancel(String sessionId, String taskId) {
    try (BoomerangClient client = getClient(sessionId)) {
      return client.cancel(taskId);
    }
  }

  /**
   * Retrieves a specific task.
   *
   * @param sessionId the session ID
   * @param taskId the task ID to retrieve
   * @return the {@link GetTaskResponse} with task details
   */
  public GetTaskResponse getTask(String sessionId, String taskId) {
    try (BoomerangClient client = getClient(sessionId)) {
      io.boomerang.proto.GetTaskResponse response = client.getTask(taskId);
      return new GetTaskResponse(
          response.getStatus().name(),
          response.getErrorMessage(),
          mapTaskDetails(response.getTask()));
    }
  }

  /**
   * Lists tasks with optional filters.
   *
   * @param sessionId the session ID
   * @param clientId optional client ID
   * @param scheduledAfter optional start time
   * @param scheduledBefore optional end time
   * @param limit optional limit
   * @param nextToken optional pagination token
   * @return the {@link ListTasksResponse}
   */
  public ListTasksResponse listTasks(
      String sessionId,
      String clientId,
      Long scheduledAfter,
      Long scheduledBefore,
      Integer limit,
      String nextToken) {
    try (BoomerangClient client = getClient(sessionId)) {
      ListTasksRequest.Builder requestBuilder = ListTasksRequest.newBuilder();
      if (clientId != null) requestBuilder.setClientId(clientId);
      if (scheduledAfter != null) requestBuilder.setScheduledAfter(scheduledAfter);
      if (scheduledBefore != null) requestBuilder.setScheduledBefore(scheduledBefore);
      if (limit != null) requestBuilder.setLimit(limit);
      if (nextToken != null) requestBuilder.setNextToken(nextToken);

      io.boomerang.proto.ListTasksResponse response = client.listTasks(requestBuilder.build());
      List<TaskDetails> taskDtos =
          response.getTasksList().stream().map(this::mapTaskDetails).collect(Collectors.toList());

      return new ListTasksResponse(
          response.getStatus().name(),
          response.getErrorMessage(),
          taskDtos,
          response.getNextToken());
    }
  }

  private TaskDetails mapTaskDetails(io.boomerang.proto.TaskDetails task) {
    if (task == null || task.getTaskId().isEmpty()) return null;
    return new TaskDetails(
        task.getTaskId(),
        task.getClientId(),
        task.getExpirationMs(),
        task.getRepeatIntervalMs(),
        Base64.getEncoder().encodeToString(task.getPayload().toByteArray()));
  }

  private BoomerangClient getClient(String sessionId) {
    if (sessionId == null || sessionId.isEmpty()) {
      throw new BoomerangException("Session ID is required");
    }
    BoomerangClient client = clientProvider.createClient(sessionId);
    client.connect();
    return client;
  }
}
