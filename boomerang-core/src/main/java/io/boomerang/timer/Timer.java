package io.boomerang.timer;

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

  /** Shuts down the timer, releasing any background resources. */
  void shutdown();

  /**
   * Checks if the timer has been shut down.
   *
   * @return {@code true} if the timer is shut down, {@code false} otherwise
   */
  boolean isShutdown();
}
