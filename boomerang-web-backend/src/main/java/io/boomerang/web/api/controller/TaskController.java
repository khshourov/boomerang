package io.boomerang.web.api.controller;

import io.boomerang.web.api.dto.GetTaskResponse;
import io.boomerang.web.api.dto.ListTasksResponse;
import io.boomerang.web.api.dto.TaskRequest;
import io.boomerang.web.api.dto.TaskResponse;
import io.boomerang.web.api.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  @Autowired private TaskService taskService;

  @PostMapping
  public ResponseEntity<TaskResponse> register(
      @RequestHeader(value = "X-Boomerang-Session-Id", required = false) String sessionId,
      @Valid @RequestBody TaskRequest taskDto) {
    if (sessionId == null || sessionId.isEmpty()) {
      return ResponseEntity.status(401).build();
    }
    try {
      TaskResponse response = taskService.register(sessionId, taskDto);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.status(401).build();
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Boolean> cancel(
      @RequestHeader(value = "X-Boomerang-Session-Id", required = false) String sessionId,
      @PathVariable String id) {
    if (sessionId == null || sessionId.isEmpty()) {
      return ResponseEntity.status(401).build();
    }
    try {
      boolean success = taskService.cancel(sessionId, id);
      return ResponseEntity.ok(success);
    } catch (Exception e) {
      return ResponseEntity.status(401).build();
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<GetTaskResponse> getTask(
      @RequestHeader(value = "X-Boomerang-Session-Id", required = false) String sessionId,
      @PathVariable String id) {
    if (sessionId == null || sessionId.isEmpty()) {
      return ResponseEntity.status(401).build();
    }
    try {
      GetTaskResponse response = taskService.getTask(sessionId, id);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.status(401).build();
    }
  }

  @GetMapping
  public ResponseEntity<ListTasksResponse> listTasks(
      @RequestHeader(value = "X-Boomerang-Session-Id", required = false) String sessionId,
      @RequestParam(required = false) String clientId,
      @RequestParam(required = false) Long scheduledAfter,
      @RequestParam(required = false) Long scheduledBefore,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String nextToken) {
    if (sessionId == null || sessionId.isEmpty()) {
      return ResponseEntity.status(401).build();
    }
    try {
      ListTasksResponse response =
          taskService.listTasks(
              sessionId, clientId, scheduledAfter, scheduledBefore, limit, nextToken);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.status(401).build();
    }
  }
}
