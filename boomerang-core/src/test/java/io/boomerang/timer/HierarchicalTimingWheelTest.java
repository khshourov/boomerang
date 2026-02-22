package io.boomerang.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HierarchicalTimingWheelTest {
  private Timer timer;
  private AtomicInteger executionCount;

  @BeforeEach
  void setUp() {
    executionCount = new AtomicInteger(0);
    timer =
        TimerFactory.createHierarchicalTimingWheel(
            task -> {
              executionCount.incrementAndGet();
              task.getTask().run();
            });
  }

  @AfterEach
  void tearDown() {
    timer.shutdown();
  }

  @Test
  void shouldExecuteImmediateTask() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    long start = System.currentTimeMillis();

    // 10ms is the base tick
    timer.add(new TimerTask(10, latch::countDown));

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    long end = System.currentTimeMillis();
    assertThat(end - start).isGreaterThanOrEqualTo(0); // 10ms delay - 10ms tick
    assertThat(executionCount.get()).isEqualTo(1);
  }

  @Test
  void shouldExecuteTaskWithDelayInSameWheel() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    long start = System.currentTimeMillis();

    // 50ms is within the 640ms (10ms * 64) range of the first wheel
    timer.add(new TimerTask(50, latch::countDown));

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    long end = System.currentTimeMillis();
    assertThat(end - start).isGreaterThanOrEqualTo(50 - 10);
    assertThat(executionCount.get()).isEqualTo(1);
  }

  @Test
  void shouldExecuteTaskWithOverflowToSecondWheel() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    long start = System.currentTimeMillis();

    // 800ms overflows to the second wheel (10ms * 64 = 640ms)
    timer.add(new TimerTask(800, latch::countDown));

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    long end = System.currentTimeMillis();
    assertThat(end - start).isGreaterThanOrEqualTo(800 - 10);
    assertThat(executionCount.get()).isEqualTo(1);
  }

  @Test
  void shouldGetTaskById() {
    TimerTask task = new TimerTask(100, () -> {});
    timer.add(task);

    Optional<TimerTask> retrieved = timer.get(task.getTaskId());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get().getTaskId()).isEqualTo(task.getTaskId());
  }

  @Test
  void shouldCancelTaskById() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    TimerTask task = new TimerTask(200, latch::countDown);
    timer.add(task);

    timer.cancel(task.getTaskId());

    // Wait a bit to ensure it doesn't fire
    assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();
    assertThat(executionCount.get()).isZero();
    assertThat(timer.get(task.getTaskId())).isEmpty();
  }

  @Test
  void shouldCancelTaskDirectly() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    TimerTask task = new TimerTask(100, latch::countDown);

    timer.add(task);
    task.cancel();

    // Wait a bit to ensure it doesn't fire
    assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();
    assertThat(executionCount.get()).isZero();
  }

  @Test
  void shouldExecuteMultipleTasksConcurrently() throws InterruptedException {
    int numTasks = 100;
    CountDownLatch latch = new CountDownLatch(numTasks);

    for (int i = 0; i < numTasks; i++) {
      timer.add(new TimerTask(10 + (i % 50), latch::countDown));
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(executionCount.get()).isEqualTo(numTasks);
  }

  @Test
  void shouldWorkWithCustomTimerConfig() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    Timer customTimer =
        TimerFactory.createHierarchicalTimingWheel(20, 32, task -> latch.countDown());
    try {
      customTimer.add(new TimerTask(40, () -> {}));
      assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    } finally {
      customTimer.shutdown();
    }
  }

  @Test
  void shouldReturnShutdownStatus() {
    assertThat(timer.isShutdown()).isFalse();
    timer.shutdown();
    assertThat(timer.isShutdown()).isTrue();
    assertThat(timer.get("any")).isEmpty();
  }

  @Test
  void shouldReplaceTimerEntry() {
    TimerTask task = new TimerTask(100, () -> {});
    TimerEntry entry1 = mock(TimerEntry.class);
    TimerEntry entry2 = mock(TimerEntry.class);

    task.setTimerEntry(entry1);
    assertThat(task.getTimerEntry()).isSameAs(entry1);

    task.setTimerEntry(entry2);
    verify(entry1).remove();
    assertThat(task.getTimerEntry()).isSameAs(entry2);
  }

  @Test
  void shouldSupportToString() {
    TimerTask task = new TimerTask(100, () -> {});
    TimerEntry entry = new TimerEntry(task);

    assertThat(task.toString())
        .contains("taskId=")
        .contains("expirationMs")
        .contains("canceled=false");
    assertThat(entry.toString()).contains("timerTask=" + task);

    task.cancel();
    assertThat(task.toString()).contains("canceled=true");
  }

  @Test
  void shouldSupportEqualsAndHashCode() {
    String taskId = "test-id";
    TimerTask task1 = new TimerTask(taskId, "client1", 100, null, 0, () -> {});
    TimerTask task2 = new TimerTask(taskId, "client1", 200, null, 0, () -> {});
    TimerTask task3 = new TimerTask("different-id", "client1", 100, null, 0, () -> {});

    assertThat(task1)
        .isEqualTo(task2)
        .hasSameHashCodeAs(task2)
        .isNotEqualTo(task3)
        .isNotEqualTo(null)
        .isNotEqualTo("some string");

    assertThat(task1.hashCode()).isNotEqualTo(task3.hashCode());
  }

  @Test
  void shouldRemoveFromWheelOnCancel() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    TimerTask task = new TimerTask(200, latch::countDown);
    timer.add(task);

    // Ensure timerEntry is set (might be async after add)
    await().until(() -> task.getTimerEntry() != null);

    task.cancel();

    assertThat(task.getTimerEntry()).isNull();
    assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();
  }

  @Test
  void shouldClonePayload() {
    byte[] original = new byte[] {1, 2, 3};
    TimerTask task = new TimerTask("task1", "client1", 100, original, 0, () -> {});

    byte[] retrieved = task.getPayload();
    assertThat(retrieved).isEqualTo(original).isNotSameAs(original);

    original[0] = 9;
    assertThat(task.getPayload()).isEqualTo(new byte[] {1, 2, 3});
  }

  @Test
  void shouldGenerateDefaultTaskId() {
    TimerTask task = new TimerTask(100, () -> {});
    assertThat(task.getTaskId()).isNotNull();
    assertThat(task.getTaskId()).isNotEmpty();
  }

  @Test
  void shouldHandleNullPayload() {
    TimerTask task = new TimerTask("task1", "client1", 100, null, 0, () -> {});
    assertThat(task.getPayload()).isNull();
  }

  @Test
  void testListWithFiltersAndPagination() {
    long now = System.currentTimeMillis();
    // 5 tasks: 3 for client1, 2 for client2. 3 are recurring, 2 are one-shot.
    TimerTask t1 =
        new TimerTask("t1", "client1", 1000, null, 0, () -> {}); // One-shot, exp now + 1000
    TimerTask t2 =
        new TimerTask("t2", "client1", 2000, null, 1000, () -> {}); // Recurring, exp now + 2000
    TimerTask t3 =
        new TimerTask("t3", "client2", 3000, null, 1000, () -> {}); // Recurring, exp now + 3000
    TimerTask t4 =
        new TimerTask("t4", "client1", 4000, null, 1000, () -> {}); // Recurring, exp now + 4000
    TimerTask t5 =
        new TimerTask("t5", "client2", 5000, null, 0, () -> {}); // One-shot, exp now + 5000

    timer.add(t1);
    timer.add(t2);
    timer.add(t3);
    timer.add(t4);
    timer.add(t5);

    // 1. List for client1 only
    ListResult<TimerTask> r1 = timer.list("client1", 0, Long.MAX_VALUE, null, 10, null);
    assertThat(r1.items())
        .hasSize(3)
        .extracting(TimerTask::getTaskId)
        .containsExactly("t1", "t2", "t4");

    // 2. List recurring tasks only
    ListResult<TimerTask> r2 = timer.list(null, 0, Long.MAX_VALUE, true, 10, null);
    assertThat(r2.items())
        .hasSize(3)
        .extracting(TimerTask::getTaskId)
        .containsExactly("t2", "t3", "t4");

    // 3. List with pagination (limit 2)
    ListResult<TimerTask> page1 = timer.list(null, 0, Long.MAX_VALUE, null, 2, null);
    assertThat(page1.items())
        .hasSize(2)
        .extracting(TimerTask::getTaskId)
        .containsExactly("t1", "t2");
    assertThat(page1.nextToken()).isNotNull();

    ListResult<TimerTask> page2 = timer.list(null, 0, Long.MAX_VALUE, null, 2, page1.nextToken());
    assertThat(page2.items())
        .hasSize(2)
        .extracting(TimerTask::getTaskId)
        .containsExactly("t3", "t4");
    assertThat(page2.nextToken()).isNotNull();

    ListResult<TimerTask> page3 = timer.list(null, 0, Long.MAX_VALUE, null, 2, page2.nextToken());
    assertThat(page3.items()).hasSize(1).extracting(TimerTask::getTaskId).containsExactly("t5");
    assertThat(page3.nextToken()).isNull();

    // 4. List within time range
    ListResult<TimerTask> range = timer.list(null, now + 1500, now + 4500, null, 10, null);
    assertThat(range.items())
        .hasSize(3)
        .extracting(TimerTask::getTaskId)
        .containsExactly("t2", "t3", "t4");
  }
}
