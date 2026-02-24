package io.boomerang.web.api.dto;

import java.util.List;

/**
 * Response object for a "list tasks" request.
 *
 * @param status the status of the request (e.g., "OK", "ERROR")
 * @param errorMessage an optional error message if the status is not "OK"
 * @param tasks the list of {@link TaskDetails} for the retrieved tasks
 * @param nextToken the token for the next page of results, or null if no more results
 */
public record ListTasksResponse(
    String status, String errorMessage, List<TaskDetails> tasks, String nextToken) {}
