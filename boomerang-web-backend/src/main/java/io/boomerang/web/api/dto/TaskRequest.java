package io.boomerang.web.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskRequest(
    @NotBlank(message = "payload is mandatory") String payload,
    Long delayMs,
    Long repeatIntervalMs) {

  public TaskRequest {
    if (delayMs == null) delayMs = 0L;
    if (repeatIntervalMs == null) repeatIntervalMs = 0L;
  }
}
