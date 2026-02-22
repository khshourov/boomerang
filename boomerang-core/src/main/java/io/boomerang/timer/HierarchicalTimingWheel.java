package io.boomerang.timer;

import io.boomerang.config.ServerConfig;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
  private final Map<String, TimerTask> idMap = new ConcurrentHashMap<>();

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
      // Only dispatch if not already cancelled and it's not an internal task (internal tasks manage
      // themselves)
      if (task.getTimerEntry() != null) {
        idMap.remove(task.getTaskId());
        dispatcher.accept(task);
      }
    }
  }

  @Override
  public void add(TimerTask task) {
    idMap.put(task.getTaskId(), task);
    addEntry(new TimerEntry(task));
  }

  @Override
  public void cancel(String taskId) {
    TimerTask task = idMap.remove(taskId);
    if (task != null) {
      task.cancel();
    }
  }

  @Override
  public Optional<TimerTask> get(String taskId) {
    return Optional.ofNullable(idMap.get(taskId));
  }

  @Override
  public ListResult<TimerTask> list(
      String clientId,
      long scheduledAfter,
      long scheduledBefore,
      Boolean isRecurring,
      int limit,
      String nextToken) {
    var stream =
        idMap.values().stream()
            .filter(task -> task.getExpirationMs() >= scheduledAfter)
            .filter(task -> task.getExpirationMs() <= scheduledBefore)
            .sorted(
                (t1, t2) -> {
                  int cmp = Long.compare(t1.getExpirationMs(), t2.getExpirationMs());
                  if (cmp == 0) return t1.getTaskId().compareTo(t2.getTaskId());
                  return cmp;
                });

    if (clientId != null) {
      stream = stream.filter(task -> clientId.equals(task.getClientId()));
    }
    if (isRecurring != null) {
      stream = stream.filter(task -> (task.getRepeatIntervalMs() > 0) == isRecurring);
    }

    if (nextToken != null && !nextToken.isEmpty()) {
      // For HTW, nextToken is expected to be expiration_taskId format to match RocksDB for
      // consistency.
      // But simpler for HTW is to just skip until we find it or something greater.
      var skipStream = stream;
      stream =
          skipStream.dropWhile(
              t -> {
                String currentToken = t.getExpirationMs() + "_" + t.getTaskId();
                return currentToken.compareTo(nextToken) <= 0;
              });
    }

    var tasks = stream.limit(limit).collect(Collectors.toList());
    String nextCursor = null;
    if (!tasks.isEmpty() && tasks.size() == limit) {
      var last = tasks.getLast();
      nextCursor = last.getExpirationMs() + "_" + last.getTaskId();
    }

    return new ListResult<>(tasks, nextCursor);
  }

  @Override
  public void shutdown() {
    workerThread.shutdownNow();
    idMap.clear();
  }

  @Override
  public boolean isShutdown() {
    return workerThread.isShutdown();
  }
}
