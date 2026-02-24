package io.boomerang.web.api.controller;

import io.boomerang.web.api.dto.GetTaskResponse;
import io.boomerang.web.api.dto.ListTasksResponse;
import io.boomerang.web.api.dto.TaskRequest;
import io.boomerang.web.api.dto.TaskResponse;
import io.boomerang.web.api.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing Boomerang tasks.
 *
 * <p>This controller provides endpoints for registering, cancelling, retrieving, and listing tasks.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  @Autowired private TaskService taskService;

  /**
   * Registers a new task.
   *
   * @param taskDto the {@link TaskRequest} containing task details
   * @return a {@link ResponseEntity} containing the {@link TaskResponse}
   */
  @PostMapping
  public ResponseEntity<TaskResponse> register(@Valid @RequestBody TaskRequest taskDto) {
    TaskResponse response = taskService.register(getSessionId(), taskDto);
    return ResponseEntity.ok(response);
  }

  /**
   * Cancels an existing task.
   *
   * @param id the task ID to cancel
   * @return a {@link ResponseEntity} containing a boolean indicating success
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Boolean> cancel(@PathVariable String id) {
    boolean success = taskService.cancel(getSessionId(), id);
    return ResponseEntity.ok(success);
  }

  /**
   * Retrieves details of a specific task.
   *
   * @param id the task ID to retrieve
   * @return a {@link ResponseEntity} containing the {@link GetTaskResponse}
   */
  @GetMapping("/{id}")
  public ResponseEntity<GetTaskResponse> getTask(@PathVariable String id) {
    GetTaskResponse response = taskService.getTask(getSessionId(), id);
    return ResponseEntity.ok(response);
  }

  /**
   * Lists tasks with optional filters and pagination.
   *
   * @param clientId optional client ID filter
   * @param scheduledAfter optional filter for tasks scheduled after a certain time
   * @param scheduledBefore optional filter for tasks scheduled before a certain time
   * @param limit optional limit on the number of results
   * @param nextToken optional token for pagination
   * @return a {@link ResponseEntity} containing the {@link ListTasksResponse}
   */
  @GetMapping
  public ResponseEntity<ListTasksResponse> listTasks(
      @RequestParam(required = false) String clientId,
      @RequestParam(required = false) Long scheduledAfter,
      @RequestParam(required = false) Long scheduledBefore,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String nextToken) {
    ListTasksResponse response =
        taskService.listTasks(
            getSessionId(), clientId, scheduledAfter, scheduledBefore, limit, nextToken);
    return ResponseEntity.ok(response);
  }

  private String getSessionId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) {
      throw new io.boomerang.client.BoomerangException("Session ID is required");
    }
    return auth.getPrincipal().toString();
  }
}
