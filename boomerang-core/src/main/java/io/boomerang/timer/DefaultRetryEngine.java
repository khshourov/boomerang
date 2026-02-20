package io.boomerang.timer;

import io.boomerang.auth.ClientStore;
import io.boomerang.model.Client;
import io.boomerang.model.RetryPolicy;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link RetryEngine} that uses {@link RetryPolicy} from the client's
 * configuration.
 *
 * @since 1.0.0
 */
public class DefaultRetryEngine implements RetryEngine {
  private static final Logger log = LoggerFactory.getLogger(DefaultRetryEngine.class);

  private final ClientStore clientStore;
  private final LongTermTaskStore longTermStore;
  private final DLQStore dlqStore;
  private final Consumer<TimerTask> rescheduler;

  /**
   * Constructs a new retry engine.
   *
   * @param clientStore the store for client information; must be non-null
   * @param longTermStore the store where task persistence is managed; must be non-null
   * @param dlqStore the store for dead-lettered tasks; must be non-null
   * @param rescheduler a consumer that adds the task back to the timer for retrying
   */
  public DefaultRetryEngine(
      ClientStore clientStore,
      LongTermTaskStore longTermStore,
      DLQStore dlqStore,
      Consumer<TimerTask> rescheduler) {
    this.clientStore = Objects.requireNonNull(clientStore, "clientStore must not be null");
    this.longTermStore = Objects.requireNonNull(longTermStore, "longTermStore must not be null");
    this.dlqStore = Objects.requireNonNull(dlqStore, "dlqStore must not be null");
    this.rescheduler = Objects.requireNonNull(rescheduler, "rescheduler must not be null");
  }

  @Override
  public void handleFailure(TimerTask task, Throwable exception) {
    String clientId = task.getClientId();
    if (clientId == null) {
      log.error("Task {} failed but has no clientId. This should not happen.", task.getTaskId());
      return;
    }

    Client client = clientStore.findById(clientId).orElse(null);
    if (client == null) {
      log.warn(
          "Client {} not found for task {}, moving to DLQ: {}",
          clientId,
          task.getTaskId(),
          exception.getMessage());
      moveToDlq(task, exception.getMessage());
      return;
    }

    RetryPolicy policy = client.retryPolicy();
    if (task.getAttemptCount() >= policy.maxAttempts()) {
      log.info("Max retry attempts reached for task {}, moving to DLQ", task.getTaskId());
      moveToDlq(task, exception.getMessage());
      return;
    }

    long delayMs = calculateBackoff(policy, task.getAttemptCount());
    log.info(
        "Task {} failed (attempt {}). Retrying in {} ms. Reason: {}",
        task.getTaskId(),
        task.getAttemptCount() + 1,
        delayMs,
        exception.getMessage());

    rescheduler.accept(task.nextAttempt(delayMs));
  }

  private void moveToDlq(TimerTask task, String errorMessage) {
    dlqStore.save(new DLQStore.DLQEntry(task, errorMessage));
    longTermStore.delete(task);
  }

  private long calculateBackoff(RetryPolicy policy, int currentAttemptCount) {
    return switch (policy.strategy()) {
      case FIXED -> policy.intervalMs();
      case EXPONENTIAL -> {
        // nextDelay = min(intervalMs * 2^(attemptCount), maxIntervalMs)
        // Here currentAttemptCount is 0 for first retry (1st failure)
        long exponentialDelay = policy.intervalMs() * (long) Math.pow(2, currentAttemptCount);
        yield Math.min(exponentialDelay, policy.maxIntervalMs());
      }
    };
  }
}
