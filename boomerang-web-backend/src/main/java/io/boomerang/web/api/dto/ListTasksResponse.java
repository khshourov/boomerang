package io.boomerang.web.api.dto;

import java.util.List;

public record ListTasksResponse(
    String status, String errorMessage, List<TaskDetails> tasks, String nextToken) {}
