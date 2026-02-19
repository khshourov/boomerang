package io.boomerang.model;

import io.boomerang.proto.CallbackConfig;
import io.boomerang.proto.DLQPolicy;
import io.boomerang.proto.RetryPolicy;
import java.time.Instant;

public record Session(
    String sessionId,
    String clientId,
    CallbackConfig callbackConfig,
    RetryPolicy retryPolicy,
    DLQPolicy dlqPolicy,
    Instant expiresAt) {

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }
}
