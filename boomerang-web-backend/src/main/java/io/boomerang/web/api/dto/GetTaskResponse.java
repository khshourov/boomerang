package io.boomerang.web.api.dto;

public record GetTaskResponse(String status, String errorMessage, TaskDetails task) {}
