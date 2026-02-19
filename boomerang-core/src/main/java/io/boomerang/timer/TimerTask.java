package io.boomerang.timer;

import java.util.Objects;

/**
 * Represents a task that can be scheduled for execution in the future.
 *
 * <p>A {@code TimerTask} is associated with an expiration time and a {@link Runnable} to execute.
 * It can be cancelled before it expires.
 *
 * @since 1.0.0
 */
public class TimerTask {
  private final long expirationMs;
  private final Runnable task;
  private volatile TimerEntry timerEntry;

  /**
   * Creates a new timer task with a specified delay.
   *
   * @param delayMs the delay in milliseconds from now when the task should expire
   * @param task the action to perform when the task expires; must be non-null
   */
  public TimerTask(long delayMs, Runnable task) {
    this.expirationMs = System.currentTimeMillis() + delayMs;
    this.task = Objects.requireNonNull(task, "Task must not be null");
  }

  /**
   * Gets the expiration time in milliseconds.
   *
   * @return the expiration time as a Unix timestamp
   */
  public long getExpirationMs() {
    return expirationMs;
  }

  /**
   * Gets the runnable task associated with this timer task.
   *
   * @return the {@link Runnable} task
   */
  public Runnable getTask() {
    return task;
  }

  /**
   * Sets the timer entry that tracks this task within a timing wheel.
   *
   * @param timerEntry the entry managing this task; can be {@code null}
   */
  public synchronized void setTimerEntry(TimerEntry timerEntry) {
    if (this.timerEntry != null && this.timerEntry != timerEntry) {
      this.timerEntry.remove();
    }
    this.timerEntry = timerEntry;
  }

  /**
   * Gets the timer entry currently tracking this task.
   *
   * @return the {@link TimerEntry}, or {@code null} if not scheduled
   */
  public synchronized TimerEntry getTimerEntry() {
    return timerEntry;
  }

  /** Cancels this task, removing it from its timing wheel if it is currently scheduled. */
  public synchronized void cancel() {
    if (timerEntry != null) {
      timerEntry.remove();
      timerEntry = null;
    }
  }

  @Override
  public String toString() {
    return "TimerTask{"
        + "expirationMs="
        + expirationMs
        + ", canceled="
        + (timerEntry == null)
        + '}';
  }
}
