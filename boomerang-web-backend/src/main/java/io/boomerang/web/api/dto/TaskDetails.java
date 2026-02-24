package io.boomerang.web.api.dto;

public record TaskDetails(
    String taskId, String clientId, Long expirationMs, Long repeatIntervalMs, String payload) {}
