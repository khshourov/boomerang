package io.boomerang.server.callback;

/**
 * Exception thrown when a callback delivery fails.
 *
 * @since 1.0.0
 */
public class CallbackException extends Exception {
  /**
   * Constructs a new callback exception with the specified detail message.
   *
   * @param message the detail message
   */
  public CallbackException(String message) {
    super(message);
  }

  /**
   * Constructs a new callback exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   */
  public CallbackException(String message, Throwable cause) {
    super(message, cause);
  }
}
