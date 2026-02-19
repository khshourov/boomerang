package io.boomerang.timer;

import java.util.Objects;

public class TimerTask {
  private final long expirationMs;
  private final Runnable task;
  private volatile TimerEntry timerEntry;

  public TimerTask(long delayMs, Runnable task) {
    this.expirationMs = System.currentTimeMillis() + delayMs;
    this.task = Objects.requireNonNull(task, "Task must not be null");
  }

  public long getExpirationMs() {
    return expirationMs;
  }

  public Runnable getTask() {
    return task;
  }

  public synchronized void setTimerEntry(TimerEntry timerEntry) {
    if (this.timerEntry != null && this.timerEntry != timerEntry) {
      this.timerEntry.remove();
    }
    this.timerEntry = timerEntry;
  }

  public synchronized TimerEntry getTimerEntry() {
    return timerEntry;
  }

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
