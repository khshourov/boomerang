package io.boomerang.timer;

import java.util.concurrent.atomic.AtomicReference;

public class TimerEntry {
  private final TimerTask timerTask;
  private final AtomicReference<TimerBucket> timerBucket = new AtomicReference<>();

  public TimerEntry next;
  public TimerEntry prev;

  public TimerEntry(TimerTask timerTask) {
    this.timerTask = timerTask;
    if (this.timerTask != null) {
      this.timerTask.setTimerEntry(this);
    }
  }

  public void setTimerBucket(TimerBucket timerBucket) {
    this.timerBucket.set(timerBucket);
  }

  public TimerBucket getTimerBucket() {
    return timerBucket.get();
  }

  public TimerTask getTimerTask() {
    return timerTask;
  }

  public synchronized void remove() {
    TimerBucket bucket = timerBucket.get();
    if (bucket != null) {
      bucket.remove(this);
      timerBucket.set(null);
    }
  }

  @Override
  public String toString() {
    return "TimerEntry{" + "timerTask=" + timerTask + '}';
  }
}
