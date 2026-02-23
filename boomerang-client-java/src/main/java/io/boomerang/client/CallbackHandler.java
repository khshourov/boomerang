package io.boomerang.client;

import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;

/**
 * Interface to be implemented by users to handle incoming task callbacks.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface CallbackHandler {

  /**
   * Invoked when a task expires and the server sends a callback.
   *
   * @param request the callback request containing the task ID and payload
   * @return the response acknowledging receipt of the callback
   */
  CallbackResponse onTaskExpired(CallbackRequest request);
}
