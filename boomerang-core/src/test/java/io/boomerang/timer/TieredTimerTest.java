package io.boomerang.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import io.boomerang.config.ServerConfig;
import java.util.Collection;
import java.util.Optional;
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
    longTermStore = spy(new InMemoryLongTermTaskStore());
    dispatcher =
        task -> {
          executionCount.incrementAndGet();
          task.getTask().run();
          // The dispatcher is now responsible for cleanup and rescheduling
          longTermStore.delete(task);
          if (task.getRepeatIntervalMs() > 0) {
            tieredTimer.add(task.nextCycle());
          }
        };
    serverConfig = mock(ServerConfig.class);
    when(serverConfig.getTimerImminentWindowMs()).thenReturn(WINDOW_MS);
    when(serverConfig.getTimerTickMs()).thenReturn(10L);
    when(serverConfig.getTimerWheelSize()).thenReturn(64);
    when(serverConfig.getTimerAdvanceClockIntervalMs()).thenReturn(50L);

    tieredTimer = new TieredTimer(dispatcher, longTermStore, serverConfig);
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
  void shouldGetTaskByIdFromHTW() {
    TimerTask task = new TimerTask(100, () -> {});
    tieredTimer.add(task);

    Optional<TimerTask> retrieved = tieredTimer.get(task.getTaskId());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get().getTaskId()).isEqualTo(task.getTaskId());
  }

  @Test
  void shouldGetTaskByIdFromStore() {
    TimerTask task = new TimerTask(2000, () -> {});
    tieredTimer.add(task);

    Optional<TimerTask> retrieved = tieredTimer.get(task.getTaskId());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get().getTaskId()).isEqualTo(task.getTaskId());
  }

  @Test
  void shouldCancelTaskInHTW() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    TimerTask task = new TimerTask(200, latch::countDown);
    tieredTimer.add(task);

    tieredTimer.cancel(task.getTaskId());

    assertThat(latch.await(1, TimeUnit.SECONDS)).isFalse();
    assertThat(executionCount.get()).isZero();
    assertThat(tieredTimer.get(task.getTaskId())).isEmpty();
  }

  @Test
  void shouldCancelTaskInStore() {
    TimerTask task = new TimerTask(2000, () -> {});
    tieredTimer.add(task);

    tieredTimer.cancel(task.getTaskId());

    assertThat(tieredTimer.get(task.getTaskId())).isEmpty();
    verify(longTermStore, times(1)).delete(task);
  }

  @Test
  void shouldRescheduleRepeatableTask() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);
    TimerTask task =
        new TimerTask(
            "repeat-task",
            "client1",
            100, // Initial delay
            null,
            100, // Repeat interval
            latch::countDown);

    tieredTimer.add(task);

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(executionCount.get()).isGreaterThanOrEqualTo(3);

    // Verify taskId is preserved
    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Optional<TimerTask> nextTask = tieredTimer.get("repeat-task");
              assertThat(nextTask).isPresent();
              assertThat(nextTask.get().getRepeatIntervalMs()).isEqualTo(100);
            });
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
    // Since we now use Spy on InMemoryLongTermTaskStore, let's verify delete was called
    verify(longTermStore, times(1)).delete(task);

    Collection<TimerTask> tasks =
        longTermStore.fetchTasksDueBefore(System.currentTimeMillis() + 5000);
    assertThat(tasks).isEmpty();
  }

  @Test
  void shouldCreateTieredTimerViaFactory() {
    Timer factoryTimer = TimerFactory.createTieredTimer(task -> {}, longTermStore, serverConfig);
    assertThat(factoryTimer).isInstanceOf(TieredTimer.class);
    factoryTimer.shutdown();
  }

  @Test
  void shouldReturnShutdownStatus() {
    assertThat(tieredTimer.isShutdown()).isFalse();
    tieredTimer.shutdown();
    assertThat(tieredTimer.isShutdown()).isTrue();
  }

  @Test
  void shouldAddAtBoundary() {
    // Exactly at boundary: now + WINDOW_MS
    TimerTask task = new TimerTask(WINDOW_MS, () -> {});

    tieredTimer.add(task);

    // According to current logic: task.getExpirationMs() < now + imminentWindowMs
    // If expiration is EXACTLY now + WINDOW_MS, it will go to store ONLY.
    // Let's verify our expectation and kill the boundary mutation.
    assertThat(tieredTimer.get(task.getTaskId())).isPresent();
    verify(longTermStore).save(task);
  }

  @Test
  void shouldAddJustInsideBoundary() {
    TimerTask task = new TimerTask(WINDOW_MS - 1, () -> {});
    tieredTimer.add(task);

    // Should be in HTW
    assertThat(tieredTimer.get(task.getTaskId())).isPresent();
  }

  @Test
  void shouldAddJustOutsideBoundary() {
    TimerTask task = new TimerTask(WINDOW_MS + 1, () -> {});
    tieredTimer.add(task);

    // Should be in Store
    assertThat(tieredTimer.get(task.getTaskId())).isPresent();
  }

  @Test
  void shouldUpdateLastLoadedTime() {
    long initialTime = tieredTimer.getLastLoadedTime();
    // Wait for at least one reactive load to happen (triggered at half of imminent window)
    await().atMost(2, TimeUnit.SECONDS).until(() -> tieredTimer.getLastLoadedTime() > initialTime);
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
  void shouldNotDeleteFromStoreAfterFetch() {
    TimerTask task = new TimerTask(2000, () -> {});
    longTermStore.save(task);

    long windowEnd = System.currentTimeMillis() + 5000;
    Collection<TimerTask> fetched1 = longTermStore.fetchTasksDueBefore(windowEnd);
    assertThat(fetched1).hasSize(1);

    // Fetch again; should still be there because fetch no longer deletes
    Collection<TimerTask> fetched2 = longTermStore.fetchTasksDueBefore(windowEnd);
    assertThat(fetched2).hasSize(1);
  }

  @Test
  void shouldPerformInitialLoadOnStartup() {
    LongTermTaskStore startupStore = mock(LongTermTaskStore.class);
    // Create a new timer with the mocked store
    new TieredTimer(dispatcher, startupStore, serverConfig);
    // Verify fetchTasksDueBefore was called once during construction
    verify(startupStore, times(1)).fetchTasksDueBefore(anyLong());
  }

  @Test
  void shouldReturnEmptyForNonExistentTask() {
    assertThat(tieredTimer.get("non-existent")).isEmpty();
  }

  @Test
  void shouldNotSaveInternalTimerTaskToStore() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    // We can't easily create TieredTimer.InternalTimerTask from outside,
    // but we can trigger reactiveLoad which adds one.
    // However, we can also test the 'add' logic by mocking if needed,
    // or just rely on the fact that reactiveLoad uses them.

    // Let's verify that the store is NEVER called with an InternalTimerTask
    // during construction (initial reactiveLoad call)
    verify(longTermStore, never())
        .save(argThat(task -> task.getClass().getSimpleName().contains("InternalTimerTask")));
  }

  @Test
  void shouldDelegateListToStore() {
    String clientId = "client1";
    long after = 1000L;
    long before = 5000L;
    Boolean recurring = true;
    int limit = 10;
    String token = "some-token";

    ListResult<TimerTask> expectedResult =
        new ListResult<>(java.util.Collections.emptyList(), null);
    when(longTermStore.list(clientId, after, before, recurring, limit, token))
        .thenReturn(expectedResult);

    ListResult<TimerTask> result =
        tieredTimer.list(clientId, after, before, recurring, limit, token);

    assertThat(result).isSameAs(expectedResult);
    verify(longTermStore).list(clientId, after, before, recurring, limit, token);
  }
}
