package io.boomerang.timer;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A container for {@link TimerEntry} instances that expire at the same relative time.
 *
 * <p>This class implements a doubly-linked list of {@link TimerEntry}s and implements {@link
 * Delayed} to be used in a {@link java.util.concurrent.DelayQueue}.
 *
 * @since 1.0.0
 */
public class TimerBucket implements Delayed {
  private final AtomicLong expiration = new AtomicLong(-1);
  private final TimerEntry root = new TimerEntry(null);

  /** Constructs a new timer bucket. */
  public TimerBucket() {
    root.next = root;
    root.prev = root;
  }

  /**
   * Sets the expiration time for this bucket.
   *
   * @param expirationMs the new expiration timestamp in milliseconds
   * @return {@code true} if the expiration was changed, {@code false} if it already matched
   */
  public boolean setExpiration(long expirationMs) {
    return expiration.getAndSet(expirationMs) != expirationMs;
  }

  /**
   * Gets the expiration timestamp for this bucket.
   *
   * @return the expiration in milliseconds
   */
  public long getExpiration() {
    return expiration.get();
  }

  /**
   * Adds a new timer entry to this bucket.
   *
   * @param entry the entry to add; must be non-null
   */
  public synchronized void add(TimerEntry entry) {
    TimerEntry last = root.prev;
    entry.next = root;
    entry.prev = last;
    entry.setTimerBucket(this);
    last.next = entry;
    root.prev = entry;
  }

  /**
   * Removes a timer entry from this bucket.
   *
   * @param entry the entry to remove; must be non-null
   */
  public synchronized void remove(TimerEntry entry) {
    if (entry.getTimerBucket() == this) {
      entry.next.prev = entry.prev;
      entry.prev.next = entry.next;
      entry.setTimerBucket(null);
      entry.next = null;
      entry.prev = null;
    }
  }

  /**
   * Clears the bucket and passes each entry to the provided consumer.
   *
   * @param reinsert a consumer that handles the flushed entries; must be non-null
   */
  public synchronized void flush(Consumer<TimerEntry> reinsert) {
    TimerEntry entry = root.next;
    while (entry != root) {
      remove(entry);
      reinsert.accept(entry);
      entry = root.next;
    }
    expiration.set(-1);
  }

  @Override
  public long getDelay(TimeUnit unit) {
    return unit.convert(
        Math.max(0, expiration.get() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
  }

  @Override
  public int compareTo(Delayed o) {
    if (o instanceof TimerBucket other) {
      return Long.compare(this.expiration.get(), other.expiration.get());
    }
    return 0;
  }
}
