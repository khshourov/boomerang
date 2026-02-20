package io.boomerang.auth;

/**
 * Exception thrown when a security-level error occurs, such as encryption or hashing failures.
 *
 * @since 1.0.0
 */
public class SecurityException extends RuntimeException {
  public SecurityException(String message) {
    super(message);
  }

  public SecurityException(String message, Throwable cause) {
    super(message, cause);
  }
}
