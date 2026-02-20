package io.boomerang.timer;

/**
 * Interface for handling task failures and orchestrating retries.
 *
 * @since 1.0.0
 */
public interface RetryEngine {
  /**
   * Handles a task failure by either rescheduling it for a retry or moving it to the DLQ.
   *
   * @param task the task that failed
   * @param exception the exception that caused the failure
   */
  void handleFailure(TimerTask task, Throwable exception);
}
