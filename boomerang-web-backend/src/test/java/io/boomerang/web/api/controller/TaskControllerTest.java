package io.boomerang.web.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.boomerang.web.api.dto.GetTaskResponse;
import io.boomerang.web.api.dto.ListTasksResponse;
import io.boomerang.web.api.dto.TaskRequest;
import io.boomerang.web.api.dto.TaskResponse;
import io.boomerang.web.api.service.TaskService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TaskService taskService;

  @Test
  void testRegisterTaskSuccess() throws Exception {
    when(taskService.register(anyString(), any(TaskRequest.class)))
        .thenReturn(new TaskResponse("task-123", "OK", 12345L, ""));

    mockMvc
        .perform(
            post("/api/tasks")
                .header("X-Boomerang-Session-Id", "test-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":\"dGVzdC1wYXlsb2Fk\", \"delayMs\":1000}"))
        .andExpect(status().isOk());
  }

  @Test
  void testRegisterTaskUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":\"dGVzdC1wYXlsb2Fk\", \"delayMs\":1000}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void testCancelTaskSuccess() throws Exception {
    when(taskService.cancel(anyString(), anyString())).thenReturn(true);

    mockMvc
        .perform(delete("/api/tasks/task-123").header("X-Boomerang-Session-Id", "test-session"))
        .andExpect(status().isOk());
  }

  @Test
  void testRegisterTaskValidationError() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks")
                .header("X-Boomerang-Session-Id", "test-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":\"\", \"delayMs\":1000}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testGetTaskSuccess() throws Exception {
    when(taskService.getTask(anyString(), anyString()))
        .thenReturn(new GetTaskResponse("OK", "", null));

    mockMvc
        .perform(get("/api/tasks/task-123").header("X-Boomerang-Session-Id", "test-session"))
        .andExpect(status().isOk());
  }

  @Test
  void testListTasksSuccess() throws Exception {
    when(taskService.listTasks(anyString(), any(), any(), any(), any(), any()))
        .thenReturn(new ListTasksResponse("OK", "", List.of(), ""));

    mockMvc
        .perform(get("/api/tasks").header("X-Boomerang-Session-Id", "test-session"))
        .andExpect(status().isOk());
  }
}
