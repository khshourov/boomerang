package io.boomerang.server.callback;

import io.boomerang.timer.TimerTask;

/**
 * Interface for the callback engine that delivers expired tasks to their registered endpoints.
 *
 * <p>The dispatcher is responsible for routing the task to the correct protocol-specific handler
 * and managing any cross-cutting concerns like logging or shared connection pooling.
 *
 * @since 1.0.0
 */
public interface CallbackDispatcher {
  /**
   * Dispatches a task to its callback endpoint.
   *
   * @param task the expired task to deliver; must be non-null
   * @throws CallbackException if the delivery fails and should be retried
   */
  void dispatch(TimerTask task) throws CallbackException;

  /** Shuts down the dispatcher and releases any resources (e.g., connection pools). */
  void shutdown();
}
