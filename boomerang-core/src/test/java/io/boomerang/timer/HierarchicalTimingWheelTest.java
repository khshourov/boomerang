package io.boomerang.timer;

import static org.assertj.core.api.Assertions.assertThat;

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
  void shouldCancelTask() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    TimerTask task = new TimerTask(100, latch::countDown);

    timer.add(task);
    task.cancel();

    // Wait a bit to ensure it doesn't fire
    assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();
    assertThat(executionCount.get()).isEqualTo(0);
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
  void shouldSupportToString() {
    TimerTask task = new TimerTask(100, () -> {});
    TimerEntry entry = new TimerEntry(task);

    assertThat(task.toString()).contains("expirationMs").contains("canceled=false");
    assertThat(entry.toString()).contains("timerTask=" + task);

    task.cancel();
    assertThat(task.toString()).contains("canceled=true");
  }
}
