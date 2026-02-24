package io.boomerang.web.api.dto;

/**
 * Response object for a successful login.
 *
 * @param sessionId the session ID to be used for subsequent requests
 * @param clientId the ID of the authenticated client
 */
public record LoginResponse(String sessionId, String clientId) {}
