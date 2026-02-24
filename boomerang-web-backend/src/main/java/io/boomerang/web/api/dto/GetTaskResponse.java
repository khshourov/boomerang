package io.boomerang.web.api.dto;

/**
 * Response object for a "get task" request.
 *
 * @param status the status of the request (e.g., "OK", "ERROR")
 * @param errorMessage an optional error message if the status is not "OK"
 * @param task the {@link TaskDetails} of the retrieved task
 */
public record GetTaskResponse(String status, String errorMessage, TaskDetails task) {}
