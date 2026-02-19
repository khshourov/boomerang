package io.boomerang.timer;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicReference;

public class TimingWheel {
  private final long tickMs;
  private final int wheelSize;
  private final long interval;
  private final TimerBucket[] buckets;
  private final DelayQueue<TimerBucket> delayQueue;
  private long currentTime;

  private final AtomicReference<TimingWheel> overflowWheel = new AtomicReference<>();

  public TimingWheel(long tickMs, int wheelSize, long startMs, DelayQueue<TimerBucket> delayQueue) {
    this.tickMs = tickMs;
    this.wheelSize = wheelSize;
    this.interval = tickMs * wheelSize;
    this.delayQueue = delayQueue;
    this.currentTime = (startMs / tickMs) * tickMs;
    this.buckets = new TimerBucket[wheelSize];
    for (int i = 0; i < wheelSize; i++) {
      buckets[i] = new TimerBucket();
    }
  }

  private synchronized void addOverflowWheel() {
    if (overflowWheel.get() == null) {
      overflowWheel.set(new TimingWheel(interval, wheelSize, currentTime, delayQueue));
    }
  }

  public boolean add(TimerEntry entry) {
    long expirationMs = entry.getTimerTask().getExpirationMs();

    if (expirationMs < currentTime + tickMs) {
      return false;
    } else if (expirationMs < currentTime + interval) {
      long virtualId = expirationMs / tickMs;
      TimerBucket bucket = buckets[(int) (virtualId % wheelSize)];
      bucket.add(entry);

      if (bucket.setExpiration(virtualId * tickMs)) {
        delayQueue.offer(bucket);
      }
      return true;
    } else {
      if (overflowWheel.get() == null) {
        addOverflowWheel();
      }
      return overflowWheel.get().add(entry);
    }
  }

  public void advanceClock(long timeMs) {
    if (timeMs >= currentTime + tickMs) {
      currentTime = (timeMs / tickMs) * tickMs;
      TimingWheel overflow = overflowWheel.get();
      if (overflow != null) {
        overflow.advanceClock(currentTime);
      }
    }
  }
}
