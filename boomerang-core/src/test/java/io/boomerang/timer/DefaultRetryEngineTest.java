package io.boomerang.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.boomerang.auth.ClientStore;
import io.boomerang.model.Client;
import io.boomerang.model.RetryPolicy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultRetryEngineTest {
  private ClientStore clientStore;
  private LongTermTaskStore longTermStore;
  private DLQStore dlqStore;
  private AtomicReference<TimerTask> resubmittedTask;
  private DefaultRetryEngine retryEngine;

  @BeforeEach
  void setUp() {
    clientStore = mock(ClientStore.class);
    longTermStore = mock(LongTermTaskStore.class);
    dlqStore = mock(DLQStore.class);
    resubmittedTask = new AtomicReference<>();
    retryEngine =
        new DefaultRetryEngine(clientStore, longTermStore, dlqStore, resubmittedTask::set);
  }

  @Test
  void shouldRetryWithFixedBackoff() {
    String clientId = "client1";
    RetryPolicy policy = new RetryPolicy(3, RetryPolicy.BackoffStrategy.FIXED, 1000, 5000);
    Client client = new Client(clientId, "hash", false, null, policy, null);
    when(clientStore.findById(clientId)).thenReturn(Optional.of(client));

    TimerTask failedTask = new TimerTask("task1", clientId, 100, null, 0, () -> {});
    long now = System.currentTimeMillis();

    retryEngine.handleFailure(failedTask, new RuntimeException("test failure"));

    assertThat(resubmittedTask.get()).isNotNull();
    assertThat(resubmittedTask.get().getAttemptCount()).isEqualTo(1);
    assertThat(resubmittedTask.get().getExpirationMs()).isGreaterThanOrEqualTo(now + 1000);
    verify(dlqStore, never()).save(any(DLQStore.DLQEntry.class));
  }

  @Test
  void shouldRetryWithExponentialBackoff() {
    String clientId = "client1";
    RetryPolicy policy = new RetryPolicy(3, RetryPolicy.BackoffStrategy.EXPONENTIAL, 1000, 10000);
    Client client = new Client(clientId, "hash", false, null, policy, null);
    when(clientStore.findById(clientId)).thenReturn(Optional.of(client));

    // Attempt 0 -> Attempt 1 (Delay 1000 * 2^0 = 1000)
    TimerTask task0 = new TimerTask("task1", clientId, 100, null, 0, () -> {});
    long now1 = System.currentTimeMillis();
    retryEngine.handleFailure(task0, new RuntimeException("failure 1"));
    assertThat(resubmittedTask.get().getAttemptCount()).isEqualTo(1);
    assertThat(resubmittedTask.get().getExpirationMs()).isGreaterThanOrEqualTo(now1 + 1000);

    // Attempt 1 -> Attempt 2 (Delay 1000 * 2^1 = 2000)
    TimerTask task1 = resubmittedTask.get();
    long now2 = System.currentTimeMillis();
    retryEngine.handleFailure(task1, new RuntimeException("failure 2"));
    assertThat(resubmittedTask.get().getAttemptCount()).isEqualTo(2);
    assertThat(resubmittedTask.get().getExpirationMs()).isGreaterThanOrEqualTo(now2 + 2000);
  }

  @Test
  void shouldMoveToDlqWhenMaxAttemptsReached() {
    String clientId = "client1";
    RetryPolicy policy = new RetryPolicy(2, RetryPolicy.BackoffStrategy.FIXED, 1000, 5000);
    Client client = new Client(clientId, "hash", false, null, policy, null);
    when(clientStore.findById(clientId)).thenReturn(Optional.of(client));

    // Task with 2 attempts already made
    TimerTask failedTask =
        TimerTask.withExpiration(
            "task1", clientId, System.currentTimeMillis(), null, 0, 2, () -> {});

    retryEngine.handleFailure(failedTask, new RuntimeException("final failure"));

    assertThat(resubmittedTask.get()).isNull();
    verify(dlqStore)
        .save(
            argThat(
                entry ->
                    entry.task().equals(failedTask)
                        && entry.errorMessage().contains("final failure")));
    verify(longTermStore).delete(failedTask);
  }

  @Test
  void shouldMoveToDlqImmediatelyIfClientNotFound() {
    String clientId = "system";
    when(clientStore.findById(clientId)).thenReturn(Optional.empty());

    TimerTask failedTask = new TimerTask(100, () -> {}); // Internal task has clientId="system"

    retryEngine.handleFailure(failedTask, new RuntimeException("system task failure"));

    verify(dlqStore)
        .save(
            argThat(
                entry ->
                    entry.task().equals(failedTask)
                        && entry.errorMessage().contains("system task failure")));
    verify(longTermStore).delete(failedTask);
  }
}
