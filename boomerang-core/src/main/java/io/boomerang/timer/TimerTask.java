package io.boomerang.timer;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a task that can be scheduled for execution in the future.
 *
 * <p>A {@code TimerTask} is associated with an expiration time and an action to perform. It can be
 * cancelled before it expires.
 *
 * @since 1.0.0
 */
public class TimerTask {
  private final String taskId;
  private final long expirationMs;
  private final byte[] payload;
  private final long repeatIntervalMs;
  private final Runnable task;
  private volatile TimerEntry timerEntry;

  /**
   * Creates a new timer task with a specified delay and a {@link Runnable}.
   *
   * <p>This constructor is primarily used for internal tasks that don't require a payload or repeat
   * interval.
   *
   * @param delayMs the delay in milliseconds from now when the task should expire
   * @param task the action to perform when the task expires; must be non-null
   */
  public TimerTask(long delayMs, Runnable task) {
    this(UUID.randomUUID().toString(), delayMs, null, 0, task);
  }

  /**
   * Creates a new timer task with full specification.
   *
   * @param taskId the unique identifier for this task; if {@code null}, a UUID will be generated
   * @param delayMs the delay in milliseconds from now when the task should expire
   * @param payload the opaque binary payload to be delivered; can be {@code null}
   * @param repeatIntervalMs the interval in milliseconds for repeated execution; 0 for no
   *     repetition
   * @param task the action to perform when the task expires; must be non-null
   */
  public TimerTask(
      String taskId, long delayMs, byte[] payload, long repeatIntervalMs, Runnable task) {
    this(taskId, System.currentTimeMillis() + delayMs, payload, repeatIntervalMs, task, true);
  }

  /**
   * Internal constructor that allows setting an absolute expiration time.
   *
   * @param taskId the unique identifier for this task
   * @param expirationMs the absolute expiration timestamp in milliseconds
   * @param payload the opaque binary payload
   * @param repeatIntervalMs the interval for repeated execution
   * @param task the action to perform
   * @param ignored dummy parameter to distinguish from the public constructor
   */
  private TimerTask(
      String taskId,
      long expirationMs,
      byte[] payload,
      long repeatIntervalMs,
      Runnable task,
      boolean ignored) {
    this.taskId = taskId != null ? taskId : UUID.randomUUID().toString();
    this.expirationMs = expirationMs;
    this.payload = payload != null ? payload.clone() : null;
    this.repeatIntervalMs = repeatIntervalMs;
    this.task = Objects.requireNonNull(task, "Task must not be null");
  }

  /**
   * Creates a new timer task with an absolute expiration timestamp.
   *
   * @param taskId the unique identifier for this task; if {@code null}, a UUID will be generated
   * @param expirationMs the absolute expiration timestamp in milliseconds
   * @param payload the opaque binary payload to be delivered; can be {@code null}
   * @param repeatIntervalMs the interval in milliseconds for repeated execution; 0 for no
   *     repetition
   * @param task the action to perform when the task expires; must be non-null
   * @return a new {@link TimerTask} instance
   */
  public static TimerTask withExpiration(
      String taskId, long expirationMs, byte[] payload, long repeatIntervalMs, Runnable task) {
    return new TimerTask(taskId, expirationMs, payload, repeatIntervalMs, task, true);
  }

  /**
   * Gets the unique identifier for this task.
   *
   * @return the task ID
   */
  public String getTaskId() {
    return taskId;
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
   * Gets the opaque binary payload associated with this task.
   *
   * @return a clone of the payload, or {@code null} if no payload was provided
   */
  public byte[] getPayload() {
    return payload != null ? payload.clone() : null;
  }

  /**
   * Gets the repeat interval for this task.
   *
   * @return the repeat interval in milliseconds; 0 if the task is not repeatable
   */
  public long getRepeatIntervalMs() {
    return repeatIntervalMs;
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
        + "taskId='"
        + taskId
        + '\''
        + ", expirationMs="
        + expirationMs
        + ", repeatIntervalMs="
        + repeatIntervalMs
        + ", payloadSize="
        + (payload != null ? payload.length : 0)
        + ", canceled="
        + (timerEntry == null)
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TimerTask timerTask = (TimerTask) o;
    return Objects.equals(taskId, timerTask.taskId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(taskId);
  }
}
