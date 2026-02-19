package io.boomerang.timer;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class TimerBucket implements Delayed {
  private final AtomicLong expiration = new AtomicLong(-1);
  private final TimerEntry root = new TimerEntry(null);

  public TimerBucket() {
    root.next = root;
    root.prev = root;
  }

  public boolean setExpiration(long expirationMs) {
    return expiration.getAndSet(expirationMs) != expirationMs;
  }

  public long getExpiration() {
    return expiration.get();
  }

  public synchronized void add(TimerEntry entry) {
    TimerEntry last = root.prev;
    entry.next = root;
    entry.prev = last;
    entry.setTimerBucket(this);
    last.next = entry;
    root.prev = entry;
  }

  public synchronized void remove(TimerEntry entry) {
    if (entry.getTimerBucket() == this) {
      entry.next.prev = entry.prev;
      entry.prev.next = entry.next;
      entry.setTimerBucket(null);
      entry.next = null;
      entry.prev = null;
    }
  }

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
