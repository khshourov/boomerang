package io.boomerang.web.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request object for registering a new task.
 *
 * @param payload the Base64 encoded payload of the task
 * @param delayMs the delay before the task should be executed in milliseconds
 * @param repeatIntervalMs the optional repeat interval for recurring tasks in milliseconds
 */
public record TaskRequest(
    @NotBlank(message = "payload is mandatory") String payload,
    Long delayMs,
    Long repeatIntervalMs) {

  public TaskRequest {
    if (delayMs == null) delayMs = 0L;
    if (repeatIntervalMs == null) repeatIntervalMs = 0L;
  }
}
