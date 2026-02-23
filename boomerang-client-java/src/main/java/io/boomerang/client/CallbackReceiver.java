package io.boomerang.client;

/**
 * Interface for components that receive callbacks from the Boomerang server.
 *
 * @since 0.1.0
 */
public interface CallbackReceiver extends AutoCloseable {

  /**
   * Starts the receiver to listen for incoming callbacks.
   *
   * @throws BoomerangException if the receiver fails to start
   */
  void start() throws BoomerangException;

  /** Stops the receiver and releases any resources. */
  @Override
  void close();
}
