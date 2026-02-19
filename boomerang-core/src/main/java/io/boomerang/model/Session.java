package io.boomerang.model;

import io.boomerang.proto.CallbackConfig;
import io.boomerang.proto.DLQPolicy;
import io.boomerang.proto.RetryPolicy;
import java.time.Instant;

/**
 * Represents an active user session within the Boomerang system.
 *
 * <p>A session is linked to a specific client and carries its own retry, dead-letter queue, and
 * callback configuration.
 *
 * @param sessionId the unique identifier for the session
 * @param clientId the ID of the client that owns the session
 * @param callbackConfig the configuration for asynchronous callbacks
 * @param retryPolicy the policy for retry attempts on callback failure
 * @param dlqPolicy the policy for dead-lettering if retries fail
 * @param expiresAt the timestamp when the session expires
 * @since 1.0.0
 */
public record Session(
    String sessionId,
    String clientId,
    CallbackConfig callbackConfig,
    RetryPolicy retryPolicy,
    DLQPolicy dlqPolicy,
    Instant expiresAt) {

  /**
   * Determines if the session has expired based on the current system time.
   *
   * @return {@code true} if the current time is after the expiration timestamp, {@code false}
   *     otherwise
   */
  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }
}
