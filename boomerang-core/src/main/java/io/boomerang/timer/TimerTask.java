package io.boomerang.timer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class TimerTask {
  private final long expirationMs;
  private final Runnable task;
  private final AtomicReference<TimerEntry> timerEntry = new AtomicReference<>();

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
    TimerEntry existing = this.timerEntry.get();
    if (existing != null && existing != timerEntry) {
      existing.remove();
    }
    this.timerEntry.set(timerEntry);
  }

  public synchronized TimerEntry getTimerEntry() {
    return timerEntry.get();
  }

  public synchronized void cancel() {
    TimerEntry entry = timerEntry.get();
    if (entry != null) {
      entry.remove();
      timerEntry.set(null);
    }
  }

  @Override
  public String toString() {
    return "TimerTask{"
        + "expirationMs="
        + expirationMs
        + ", canceled="
        + (timerEntry.get() == null)
        + '}';
  }
}
