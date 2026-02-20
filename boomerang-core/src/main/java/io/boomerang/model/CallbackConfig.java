package io.boomerang.model;

/**
 * Custom data class for callback configuration.
 *
 * @param protocol the protocol to use for the callback
 * @param endpoint the target endpoint for the callback
 * @since 1.0.0
 */
public record CallbackConfig(Protocol protocol, String endpoint) {
  /** Supported protocols for callbacks. */
  public enum Protocol {
    TCP,
    GRPC,
    HTTP,
    UDP
  }
}
