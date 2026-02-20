package io.boomerang.timer;

/**
 * Exception thrown when a storage-level error occurs.
 *
 * @since 1.0.0
 */
public class StorageException extends RuntimeException {
  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
