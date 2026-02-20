package io.boomerang.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.boomerang.config.ServerConfig;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocksDBTieredTimerIntegrationTest {
  @TempDir Path tempDir;

  private TieredTimer tieredTimer;
  private final java.util.concurrent.atomic.AtomicReference<LongTermTaskStore> currentStore =
      new java.util.concurrent.atomic.AtomicReference<>();
  private AtomicInteger executionCount;
  private Consumer<TimerTask> dispatcher;
  private ServerConfig serverConfig;
  private static final long WINDOW_MS = 1000;

  @BeforeEach
  void setUp() {
    executionCount = new AtomicInteger(0);
    // Use currentStore reference to avoid stale references after restarts
    dispatcher =
        task -> {
          executionCount.incrementAndGet();
          LongTermTaskStore store = currentStore.get();
          if (store != null) {
            store.delete(task);
          }
          if ("task-to-recover".equals(task.getTaskId())) {
            recoveryLatch.countDown();
          }
        };
    serverConfig = mock(ServerConfig.class);
    when(serverConfig.getRocksDbPath()).thenReturn(tempDir.resolve("db").toString());
    when(serverConfig.getTimerImminentWindowMs()).thenReturn(WINDOW_MS);
    when(serverConfig.getTimerTickMs()).thenReturn(10L);
    when(serverConfig.getTimerWheelSize()).thenReturn(64);
    when(serverConfig.getTimerAdvanceClockIntervalMs()).thenReturn(50L);

    RocksDBLongTermTaskStore rocksDbStore = new RocksDBLongTermTaskStore(serverConfig);
    currentStore.set(rocksDbStore);
    tieredTimer = new TieredTimer(dispatcher, rocksDbStore, serverConfig);
  }

  private CountDownLatch recoveryLatch = new CountDownLatch(1);

  @AfterEach
  void tearDown() {
    if (tieredTimer != null) {
      tieredTimer.shutdown();
    }
    LongTermTaskStore store = currentStore.getAndSet(null);
    if (store instanceof RocksDBLongTermTaskStore rocks) {
      rocks.close();
    }
  }

  @Test
  void shouldRecoverTasksFromRocksDBOnStartup() throws InterruptedException {
    // 1. Create a task that is just outside the window
    recoveryLatch = new CountDownLatch(1);
    TimerTask task = new TimerTask("task-to-recover", "client1", 1500, null, 0, () -> {});
    currentStore.get().save(task);

    // 2. Restart the timer (simulated by creating a new one with same store)
    // First shutdown existing
    tieredTimer.shutdown();

    // Close the old store and open a new one
    LongTermTaskStore oldStore = currentStore.get();
    if (oldStore instanceof RocksDBLongTermTaskStore rocks) {
      rocks.close();
    }

    RocksDBLongTermTaskStore newStore = new RocksDBLongTermTaskStore(serverConfig);
    currentStore.set(newStore);
    TieredTimer newTimer = new TieredTimer(dispatcher, newStore, serverConfig);

    try {
      // Wait for the task to be loaded and executed.
      assertThat(recoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(executionCount.get()).isEqualTo(1);
    } finally {
      newTimer.shutdown();
      newStore.close();
      currentStore.set(null);
      tieredTimer = null;
    }
  }
}
