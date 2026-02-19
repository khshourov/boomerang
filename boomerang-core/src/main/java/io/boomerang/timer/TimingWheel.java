package io.boomerang.timer;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A hierarchical timing wheel for efficient timeout management.
 *
 * <p>A timing wheel maintains a circular list of {@link TimerBucket}s. Each bucket represents a
 * specific time interval. As time progresses, the wheel's clock advances and the appropriate
 * bucket's tasks are either executed or moved to a more granular wheel.
 *
 * @since 1.0.0
 */
public class TimingWheel {
  private final long tickMs;
  private final int wheelSize;
  private final long interval;
  private final TimerBucket[] buckets;
  private final DelayQueue<TimerBucket> delayQueue;
  private volatile long currentTime;

  private final AtomicReference<TimingWheel> overflowWheel = new AtomicReference<>();

  /**
   * Constructs a new timing wheel.
   *
   * @param tickMs the duration of a single tick in milliseconds; must be positive
   * @param wheelSize the number of buckets in the wheel; must be positive
   * @param startMs the start time for the wheel in milliseconds
   * @param delayQueue a queue for buckets scheduled for expiration; must be non-null
   */
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

  /**
   * Adds a new timer entry to the wheel or its overflow wheels.
   *
   * @param entry the entry representing the task to be added; must be non-null
   * @return {@code true} if the task was successfully added, {@code false} if it has already
   *     expired
   */
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

  /**
   * Advances the internal clock to the specified time.
   *
   * @param timeMs the current time in milliseconds to advance to
   */
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
