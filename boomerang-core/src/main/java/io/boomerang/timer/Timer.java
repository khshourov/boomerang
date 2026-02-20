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

  /** Shuts down the timer, releasing any background resources. */
  void shutdown();

  /**
   * Checks if the timer has been shut down.
   *
   * @return {@code true} if the timer is shut down, {@code false} otherwise
   */
  boolean isShutdown();
}
