package io.boomerang.timer;

import io.boomerang.config.ServerConfig;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * An implementation of a {@link Timer} that uses a hierarchical timing wheel for efficient timeout
 * management.
 *
 * <p>This implementation can handle a large number of concurrent timers with $O(1)$ time complexity
 * for insertions and deletions. It supports multiple levels of wheels with increasing granularity.
 *
 * @since 1.0.0
 */
public class HierarchicalTimingWheel implements Timer {
  private final Consumer<TimerTask> dispatcher;
  private final DelayQueue<TimerBucket> delayQueue = new DelayQueue<>();
  private final TimingWheel timingWheel;
  private final ExecutorService workerThread;
  private final long advanceClockIntervalMs;

  /**
   * Constructs a hierarchical timing wheel with default configuration.
   *
   * @param tickMs the duration of a single tick in the innermost wheel; must be positive
   * @param wheelSize the number of buckets in each wheel; must be positive
   * @param dispatcher a consumer that executes or dispatches tasks when they expire; must be
   *     non-null
   */
  public HierarchicalTimingWheel(long tickMs, int wheelSize, Consumer<TimerTask> dispatcher) {
    this(tickMs, wheelSize, dispatcher, null);
  }

  /**
   * Constructs a hierarchical timing wheel with custom configuration.
   *
   * @param tickMs the duration of a single tick in the innermost wheel; must be positive
   * @param wheelSize the number of buckets in each wheel; must be positive
   * @param dispatcher a consumer that executes or dispatches tasks when they expire; must be
   *     non-null
   * @param serverConfig the server configuration for interval tuning; can be {@code null}
   */
  public HierarchicalTimingWheel(
      long tickMs, int wheelSize, Consumer<TimerTask> dispatcher, ServerConfig serverConfig) {
    this.dispatcher = dispatcher;
    this.timingWheel = new TimingWheel(tickMs, wheelSize, System.currentTimeMillis(), delayQueue);
    this.advanceClockIntervalMs =
        serverConfig != null ? serverConfig.getTimerAdvanceClockIntervalMs() : 200;
    this.workerThread =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "timer-worker");
              t.setDaemon(true);
              return t;
            });
    this.workerThread.submit(this::run);
  }

  private void run() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        advanceClock(advanceClockIntervalMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void advanceClock(long timeoutMs) throws InterruptedException {
    TimerBucket bucket = delayQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    if (bucket != null) {
      timingWheel.advanceClock(bucket.getExpiration());
      bucket.flush(this::addEntry);
    }
  }

  private void addEntry(TimerEntry entry) {
    if (!timingWheel.add(entry)) {
      TimerTask task = entry.getTimerTask();
      if (task.getTimerEntry() != null) {
        dispatcher.accept(task);
      }
    }
  }

  @Override
  public void add(TimerTask task) {
    addEntry(new TimerEntry(task));
  }

  @Override
  public void shutdown() {
    workerThread.shutdownNow();
  }
}
