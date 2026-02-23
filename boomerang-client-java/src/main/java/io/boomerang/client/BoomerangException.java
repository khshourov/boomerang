package io.boomerang.client;

/**
 * Base exception for all Boomerang-related errors.
 *
 * @since 0.1.0
 */
public class BoomerangException extends RuntimeException {
  public BoomerangException(String message) {
    super(message);
  }

  public BoomerangException(String message, Throwable cause) {
    super(message, cause);
  }
}
