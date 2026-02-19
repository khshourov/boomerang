package io.boomerang.timer;

import io.boomerang.config.ServerConfig;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class HierarchicalTimingWheel implements Timer {
  private final Consumer<TimerTask> dispatcher;
  private final DelayQueue<TimerBucket> delayQueue = new DelayQueue<>();
  private final TimingWheel timingWheel;
  private final ExecutorService workerThread;
  private final long advanceClockIntervalMs;

  public HierarchicalTimingWheel(long tickMs, int wheelSize, Consumer<TimerTask> dispatcher) {
    this(tickMs, wheelSize, dispatcher, null);
  }

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
