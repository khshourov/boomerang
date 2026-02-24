package io.boomerang.web.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "clientId is mandatory") String clientId,
    @NotBlank(message = "password is mandatory") String password) {}
