package io.boomerang.timer;

public class TimerEntry {
  private final TimerTask timerTask;
  private volatile TimerBucket timerBucket;

  public TimerEntry next;
  public TimerEntry prev;

  public TimerEntry(TimerTask timerTask) {
    this.timerTask = timerTask;
    if (this.timerTask != null) {
      this.timerTask.setTimerEntry(this);
    }
  }

  public synchronized void setTimerBucket(TimerBucket timerBucket) {
    this.timerBucket = timerBucket;
  }

  public synchronized TimerBucket getTimerBucket() {
    return timerBucket;
  }

  public TimerTask getTimerTask() {
    return timerTask;
  }

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
