package io.boomerang.timer;

/**
 * An entry in a {@link TimerBucket} that wraps a {@link TimerTask}.
 *
 * <p>This class implements a doubly-linked list node to allow $O(1)$ removal from a bucket.
 *
 * @since 1.0.0
 */
public class TimerEntry {
  private final TimerTask timerTask;
  private volatile TimerBucket timerBucket;

  /** The next entry in the bucket's linked list. */
  public TimerEntry next;

  /** The previous entry in the bucket's linked list. */
  public TimerEntry prev;

  /**
   * Constructs a new timer entry for the specified task.
   *
   * @param timerTask the task to be wrapped by this entry; can be {@code null} for root nodes
   */
  public TimerEntry(TimerTask timerTask) {
    this.timerTask = timerTask;
    if (this.timerTask != null) {
      this.timerTask.setTimerEntry(this);
    }
  }

  /**
   * Sets the bucket that currently contains this entry.
   *
   * @param timerBucket the bucket containing this entry; can be {@code null}
   */
  public synchronized void setTimerBucket(TimerBucket timerBucket) {
    this.timerBucket = timerBucket;
  }

  /**
   * Gets the bucket that currently contains this entry.
   *
   * @return the {@link TimerBucket}, or {@code null} if not in a bucket
   */
  public synchronized TimerBucket getTimerBucket() {
    return timerBucket;
  }

  /**
   * Gets the timer task wrapped by this entry.
   *
   * @return the {@link TimerTask}
   */
  public TimerTask getTimerTask() {
    return timerTask;
  }

  /** Removes this entry from its current bucket. */
  public synchronized void remove() {
    if (timerBucket != null) {
      timerBucket.remove(this);
      timerBucket = null;
    }
  }

  @Override
  public String toString() {
    return "TimerEntry{" + "timerTask=" + timerTask + '}';
  }
}
