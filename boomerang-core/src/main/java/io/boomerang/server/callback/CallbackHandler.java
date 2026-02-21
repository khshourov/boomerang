package io.boomerang.server.callback;

import io.boomerang.model.CallbackConfig;
import io.boomerang.timer.TimerTask;

/**
 * Interface for a protocol-specific handler that delivers a task to a callback endpoint.
 *
 * @since 1.0.0
 */
public interface CallbackHandler {
  /**
   * Delivers the expired task to the given endpoint.
   *
   * @param task the expired task; must be non-null
   * @param config the callback configuration for the task's client; must be non-null
   * @throws CallbackException if delivery fails and should be retried by the dispatcher
   */
  void handle(TimerTask task, CallbackConfig config) throws CallbackException;

  /**
   * Returns the protocol this handler is responsible for.
   *
   * @return the {@link CallbackConfig.Protocol} handled by this implementation
   */
  CallbackConfig.Protocol getProtocol();

  /** Shuts down the handler and releases any local resources. */
  default void shutdown() {}
}
