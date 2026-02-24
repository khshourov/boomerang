package io.boomerang.web.api.dto;

public record TaskResponse(
    String taskId, String status, Long scheduledTimeMs, String errorMessage) {}
