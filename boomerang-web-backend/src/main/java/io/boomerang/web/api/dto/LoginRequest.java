package io.boomerang.web.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request object for client login.
 *
 * @param clientId the unique ID of the client
 * @param password the password for the client
 */
public record LoginRequest(
    @NotBlank(message = "clientId is mandatory") String clientId,
    @NotBlank(message = "password is mandatory") String password) {}
