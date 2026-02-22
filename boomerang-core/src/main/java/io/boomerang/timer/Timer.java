package io.boomerang.timer;

import java.util.Optional;

/**
 * Interface for a timer that can schedule tasks for future execution.
 *
 * @since 1.0.0
 */
public interface Timer {
  /**
   * Schedules a task for execution after its specified delay.
   *
   * @param task the task to schedule; must be non-null
   */
  void add(TimerTask task);

  /**
   * Cancels a previously scheduled task.
   *
   * <p>If the task has already fired or does not exist, this method has no effect.
   *
   * @param taskId the unique identifier of the task to cancel; must be non-null
   */
  void cancel(String taskId);

  /**
   * Retrieves a task by its unique identifier.
   *
   * @param taskId the unique ID of the task to find; must be non-null
   * @return an {@link Optional} containing the task if found, or empty otherwise
   */
  Optional<TimerTask> get(String taskId);

  /**
   * Lists tasks based on the provided filters and pagination.
   *
   * @param clientId filter by client; if null, lists tasks for all clients (admin)
   * @param scheduledAfter start of interval (Unix epoch ms)
   * @param scheduledBefore end of interval (Unix epoch ms)
   * @param isRecurring filter by one-shot (false) vs recurring (true); null means both
   * @param limit max tasks to return
   * @param nextToken opaque cursor for pagination
   * @return a result containing the tasks and the next cursor
   */
  ListResult<TimerTask> list(
      String clientId,
      long scheduledAfter,
      long scheduledBefore,
      Boolean isRecurring,
      int limit,
      String nextToken);

  /** Shuts down the timer, releasing any background resources. */
  void shutdown();

  /**
   * Checks if the timer has been shut down.
   *
   * @return {@code true} if the timer is shut down, {@code false} otherwise
   */
  boolean isShutdown();
}
