package io.boomerang.web.api.dto;

/**
 * Represents the detailed information of a task.
 *
 * @param taskId the unique ID of the task
 * @param clientId the ID of the client that owns the task
 * @param expirationMs the expiration time of the task in milliseconds
 * @param repeatIntervalMs the repeat interval of the task in milliseconds
 * @param payload the Base64 encoded payload of the task
 */
public record TaskDetails(
    String taskId, String clientId, Long expirationMs, Long repeatIntervalMs, String payload) {}
