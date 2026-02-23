package io.boomerang.client;

import io.boomerang.proto.Status;
import java.util.Optional;

/**
 * Base exception for all Boomerang-related errors.
 *
 * @since 0.1.0
 */
public class BoomerangException extends RuntimeException {
  private final Status status;

  public BoomerangException(String message) {
    this(message, null, null);
  }

  public BoomerangException(String message, Throwable cause) {
    this(message, cause, null);
  }

  public BoomerangException(Status status, String message) {
    this(message, null, status);
  }

  private BoomerangException(String message, Throwable cause, Status status) {
    super(message, cause);
    this.status = status;
  }

  /**
   * Returns the status associated with this exception, if any.
   *
   * @return an Optional containing the Status
   */
  public Optional<Status> getStatus() {
    return Optional.ofNullable(status);
  }
}
