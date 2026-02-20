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
  private LongTermTaskStore rocksDbStore;
  private AtomicInteger executionCount;
  private Consumer<TimerTask> dispatcher;
  private ServerConfig serverConfig;
  private static final long WINDOW_MS = 1000;

  @BeforeEach
  void setUp() {
    executionCount = new AtomicInteger(0);
    // In a real scenario, the dispatcher would use the taskId or payload to determine the action.
    // For this test, we'll increment the executionCount and handle the recovery latch by taskId.
    dispatcher =
        task -> {
          executionCount.incrementAndGet();
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

    rocksDbStore = new RocksDBLongTermTaskStore(serverConfig);
    tieredTimer = new TieredTimer(dispatcher, rocksDbStore, serverConfig);
  }

  private CountDownLatch recoveryLatch = new CountDownLatch(1);

  @AfterEach
  void tearDown() {
    if (tieredTimer != null) {
      tieredTimer.shutdown();
    }
    if (rocksDbStore instanceof RocksDBLongTermTaskStore rocks) {
      rocks.close();
    }
  }

  @Test
  void shouldRecoverTasksFromRocksDBOnStartup() throws InterruptedException {
    // 1. Create a task that is just outside the window
    recoveryLatch = new CountDownLatch(1);
    TimerTask task = new TimerTask("task-to-recover", 1500, null, 0, () -> {});
    rocksDbStore.save(task);

    // 2. Restart the timer (simulated by creating a new one with same store)
    // First shutdown existing
    tieredTimer.shutdown();

    // We need to re-open RocksDB or reuse the same handle.
    // Let's create a NEW store and NEW timer pointed at the same path.
    if (rocksDbStore instanceof RocksDBLongTermTaskStore rocks) {
      rocks.close();
    }

    RocksDBLongTermTaskStore newStore = new RocksDBLongTermTaskStore(serverConfig);
    TieredTimer newTimer = new TieredTimer(dispatcher, newStore, serverConfig);

    try {
      // Wait for the task to be loaded and executed.
      // It's scheduled at +1500ms. reactiveLoad runs at 0ms (fetch <1000ms), 500ms (fetch <1500ms),
      // 1000ms (fetch <2000ms).
      // It should be picked up at 1000ms load or later.

      assertThat(recoveryLatch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(executionCount.get()).isEqualTo(1);
    } finally {
      newTimer.shutdown();
      newStore.close();
      rocksDbStore = null; // Prevent tearDown from closing it again
      tieredTimer = null;
    }
  }
}
