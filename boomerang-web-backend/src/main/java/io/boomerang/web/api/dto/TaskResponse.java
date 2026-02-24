package io.boomerang.web.api.dto;

/**
 * Response object for a task registration request.
 *
 * @param taskId the ID of the newly registered task
 * @param status the status of the registration (e.g., "OK", "ERROR")
 * @param scheduledTimeMs the scheduled execution time in milliseconds
 * @param errorMessage an optional error message if the status is not "OK"
 */
public record TaskResponse(
    String taskId, String status, Long scheduledTimeMs, String errorMessage) {}
