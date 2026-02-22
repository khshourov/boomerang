package io.boomerang.timer;

import io.boomerang.config.ServerConfig;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tiered timer that combines an in-memory hierarchical timing wheel with long-term storage.
 *
 * <p>Tasks are always saved to the {@link LongTermTaskStore} to ensure durability. If a task is
 * scheduled within the {@code imminentWindowMs}, it is also added to the {@link
 * HierarchicalTimingWheel} for efficient execution.
 *
 * @since 1.0.0
 */
public class TieredTimer implements Timer {
  private static final Logger log = LoggerFactory.getLogger(TieredTimer.class);

  private final HierarchicalTimingWheel imminentTimer;
  private final LongTermTaskStore longTermStore;
  private final long imminentWindowMs;
  private final long loadThresholdMs;
  private final Consumer<TimerTask> dispatcher;
  private final AtomicLong lastLoadedTime;

  /**
   * Constructs a new tiered timer.
   *
   * @param dispatcher the consumer for expired tasks; must be non-null
   * @param longTermStore the store for long-term tasks; must be non-null
   * @param serverConfig the server configuration for timer tuning; must be non-null
   */
  public TieredTimer(
      Consumer<TimerTask> dispatcher, LongTermTaskStore longTermStore, ServerConfig serverConfig) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.longTermStore = Objects.requireNonNull(longTermStore, "longTermStore must not be null");
    this.imminentWindowMs = serverConfig.getTimerImminentWindowMs();
    // Load tasks for the next imminent window when half of it has passed
    this.loadThresholdMs = imminentWindowMs / 2;
    this.lastLoadedTime = new AtomicLong(System.currentTimeMillis());

    this.imminentTimer =
        new HierarchicalTimingWheel(
            serverConfig.getTimerTickMs(),
            serverConfig.getTimerWheelSize(),
            this::handleExpiredTask,
            serverConfig);

    // Initial load to recover tasks already due in the imminent window
    reactiveLoad();
  }

  private void handleExpiredTask(TimerTask task) {
    if (task instanceof InternalTimerTask) {
      task.getTask().run();
      return;
    }

    // dispatcher.accept(task) will handle execution, errors/retries, deletion, and rescheduling.
    dispatcher.accept(task);
  }

  private static class InternalTimerTask extends TimerTask {
    public InternalTimerTask(long delayMs, Runnable task) {
      super(delayMs, task);
    }
  }

  private void scheduleReactiveLoad() {
    long nextLoadDelay = loadThresholdMs;
    TimerTask loadTask = new InternalTimerTask(nextLoadDelay, this::reactiveLoad);
    imminentTimer.add(loadTask);
  }

  private void reactiveLoad() {
    long now = System.currentTimeMillis();
    long windowEnd = now + imminentWindowMs;

    log.debug("Reactive load triggered at {}, fetching tasks due before {}", now, windowEnd);

    Collection<TimerTask> tasks = longTermStore.fetchTasksDueBefore(windowEnd);
    if (!tasks.isEmpty()) {
      int addedCount = 0;
      for (TimerTask task : tasks) {
        if (imminentTimer.get(task.getTaskId()).isEmpty()) {
          imminentTimer.add(task);
          addedCount++;
        }
      }
      log.info(
          "Transitioned {} new tasks from long-term store to HTW ({} total fetched)",
          addedCount,
          tasks.size());
    }

    lastLoadedTime.set(now);
    scheduleReactiveLoad(); // Reschedule for the next window
  }

  @Override
  public void add(TimerTask task) {
    long now = System.currentTimeMillis();
    // Always save to the long-term store first to ensure durability across crashes.
    // If the app crashes while the task is in memory, we can reload it from the store on restart.
    if (!(task instanceof InternalTimerTask)) {
      longTermStore.save(task);
    }

    if (task.getExpirationMs() < now + imminentWindowMs) {
      log.debug("Adding task with expiration {} to imminent timer", task.getExpirationMs());
      imminentTimer.add(task);
    } else {
      log.debug(
          "Adding long-term task with expiration {} to store (already saved)",
          task.getExpirationMs());
    }
  }

  @Override
  public void cancel(String taskId) {
    log.debug("Cancelling task {}", taskId);
    // Remove from HTW first (if it's there)
    imminentTimer.cancel(taskId);
    // Remove from long-term store
    longTermStore.findById(taskId).ifPresent(longTermStore::delete);
  }

  @Override
  public Optional<TimerTask> get(String taskId) {
    // Check HTW first as it's the most likely place for "imminent" tasks
    Optional<TimerTask> task = imminentTimer.get(taskId);
    if (task.isPresent()) {
      return task;
    }
    // Then check long-term store
    return longTermStore.findById(taskId);
  }

  @Override
  public ListResult<TimerTask> list(
      String clientId,
      long scheduledAfter,
      long scheduledBefore,
      Boolean isRecurring,
      int limit,
      String nextToken) {
    return longTermStore.list(
        clientId, scheduledAfter, scheduledBefore, isRecurring, limit, nextToken);
  }

  @Override
  public void shutdown() {
    imminentTimer.shutdown();
  }

  long getLastLoadedTime() {
    return lastLoadedTime.get();
  }

  @Override
  public boolean isShutdown() {
    return imminentTimer.isShutdown();
  }
}
