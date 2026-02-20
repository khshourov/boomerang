package io.boomerang.model;

/**
 * Represents an authenticated client in the system.
 *
 * @param clientId the unique identifier for the client
 * @param hashedPassword the BCrypt hashed password
 * @param isAdmin whether this client has administrative privileges
 * @param callbackConfig the default callback configuration for this client
 * @param retryPolicy the default retry policy for this client
 * @param dlqPolicy the default dead-letter queue policy for this client
 * @since 1.0.0
 */
public record Client(
    String clientId,
    String hashedPassword,
    boolean isAdmin,
    CallbackConfig callbackConfig,
    RetryPolicy retryPolicy,
    DLQPolicy dlqPolicy) {}
