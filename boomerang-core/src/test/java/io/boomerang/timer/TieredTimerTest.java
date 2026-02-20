package io.boomerang.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.boomerang.config.ServerConfig;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TieredTimerTest {
  private TieredTimer tieredTimer;
  private LongTermTaskStore longTermStore;
  private AtomicInteger executionCount;
  private Consumer<TimerTask> dispatcher;
  private ServerConfig serverConfig;
  private static final long WINDOW_MS = 1000; // Small window for testing

  @BeforeEach
  void setUp() {
    executionCount = new AtomicInteger(0);
    dispatcher =
        task -> {
          executionCount.incrementAndGet();
          task.getTask().run();
        };
    serverConfig = new ServerConfig(null);
    longTermStore = spy(new InMemoryLongTermTaskStore());
    tieredTimer = new TieredTimer(dispatcher, longTermStore, WINDOW_MS, serverConfig);
  }

  @AfterEach
  void tearDown() {
    tieredTimer.shutdown();
  }

  @Test
  void shouldAddImminentTaskToStoreAndHTW() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    TimerTask task = new TimerTask(100, latch::countDown); // 100ms < 1000ms

    tieredTimer.add(task);

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(executionCount.get()).isEqualTo(1);
    // Verify it was saved to the store for durability
    verify(longTermStore, times(1)).save(task);
    // Verify it was deleted from the store after execution
    verify(longTermStore, times(1)).delete(task);
  }

  @Test
  void shouldAddLongTermTaskToStoreOnlyInitially() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    // 2000ms > 1000ms window
    TimerTask task = new TimerTask(2000, latch::countDown);

    tieredTimer.add(task);

    // Should not fire immediately
    assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();
    verify(longTermStore, times(1)).save(task);
    // Should NOT be deleted yet
    verify(longTermStore, never()).delete(task);
  }

  @Test
  void shouldTransitionAndThenDeleteLongTermTask() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    // 1500ms > 1000ms window
    TimerTask task = new TimerTask(1500, latch::countDown);

    tieredTimer.add(task);

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(executionCount.get()).isEqualTo(1);
    // Saved during add()
    verify(longTermStore, atLeastOnce()).save(task);
    // Deleted during handleExpiredTask()
    verify(longTermStore, atLeastOnce()).delete(task);
  }

  @Test
  void shouldHandleMultipleTasksWithDifferentDelays() throws InterruptedException {
    CountDownLatch imminentLatch = new CountDownLatch(1);
    CountDownLatch longTermLatch = new CountDownLatch(1);

    TimerTask imminentTask = new TimerTask(100, imminentLatch::countDown);
    TimerTask longTermTask = new TimerTask(1800, longTermLatch::countDown);

    tieredTimer.add(imminentTask);
    tieredTimer.add(longTermTask);

    assertThat(imminentLatch.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(longTermLatch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(executionCount.get()).isEqualTo(2);
  }

  @Test
  void shouldDeleteFromLongTermStore() {
    TimerTask task = new TimerTask(2000, () -> {});
    tieredTimer.add(task);
    verify(longTermStore, times(1)).save(task);

    longTermStore.delete(task);
    Collection<TimerTask> tasks =
        longTermStore.fetchTasksDueBefore(System.currentTimeMillis() + 5000);
    assertThat(tasks).isEmpty();
  }

  @Test
  void shouldCreateTieredTimerViaFactory() {
    Timer factoryTimer =
        TimerFactory.createTieredTimer(task -> {}, longTermStore, 1000, serverConfig);
    assertThat(factoryTimer).isInstanceOf(TieredTimer.class);
    factoryTimer.shutdown();
  }

  @Test
  void shouldShutdownImminentTimer() {
    tieredTimer.shutdown();
    assertThat(tieredTimer.isShutdown()).isTrue();
  }

  @Test
  void shouldUpdateLastLoadedTime() throws InterruptedException {
    long initialTime = tieredTimer.getLastLoadedTime();
    // Wait for at least one reactive load to happen (at 500ms)
    Thread.sleep(1000);
    assertThat(tieredTimer.getLastLoadedTime()).isGreaterThan(initialTime);
  }

  @Test
  void shouldHandleDeleteForNonExistentTask() {
    TimerTask task = new TimerTask(2000, () -> {});
    // Don't add it
    longTermStore.delete(task);
    // Should not throw
  }

  @Test
  void shouldHandleDeleteWhenBucketHasMultipleTasks() {
    TimerTask task1 = new TimerTask(2000, () -> {});
    TimerTask task2 = new TimerTask(2000, () -> {}); // Same expiration

    longTermStore.save(task1);
    longTermStore.save(task2);

    longTermStore.delete(task1);

    Collection<TimerTask> remaining =
        longTermStore.fetchTasksDueBefore(2500 + System.currentTimeMillis());
    assertThat(remaining).containsExactly(task2);
  }

  @Test
  void shouldClearStoreAfterFetch() {
    TimerTask task = new TimerTask(2000, () -> {});
    longTermStore.save(task);

    long windowEnd = System.currentTimeMillis() + 5000;
    Collection<TimerTask> fetched1 = longTermStore.fetchTasksDueBefore(windowEnd);
    assertThat(fetched1).hasSize(1);

    Collection<TimerTask> fetched2 = longTermStore.fetchTasksDueBefore(windowEnd);
    assertThat(fetched2).isEmpty();
  }
}
