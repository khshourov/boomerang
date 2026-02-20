package io.boomerang.model;

/**
 * Custom data class for retry policies.
 *
 * @param maxAttempts the maximum number of retry attempts
 * @param strategy the backoff strategy to use
 * @param intervalMs the retry interval in milliseconds
 * @param maxIntervalMs the maximum interval cap for exponential backoff
 * @since 1.0.0
 */
public record RetryPolicy(
    int maxAttempts, BackoffStrategy strategy, long intervalMs, long maxIntervalMs) {

  /** Supported backoff strategies for retries. */
  public enum BackoffStrategy {
    FIXED,
    EXPONENTIAL
  }
}
