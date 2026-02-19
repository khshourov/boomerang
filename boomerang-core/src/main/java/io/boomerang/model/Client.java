package io.boomerang.model;

/**
 * Represents an authenticated client in the system.
 *
 * @param clientId the unique identifier for the client
 * @param hashedPassword the BCrypt hashed password
 * @param isAdmin whether this client has administrative privileges
 * @since 1.0.0
 */
public record Client(String clientId, String hashedPassword, boolean isAdmin) {}
