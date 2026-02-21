package io.boomerang.server;

import io.boomerang.model.CallbackConfig;
import io.boomerang.model.DLQPolicy;
import io.boomerang.model.RetryPolicy;

/**
 * Utility class for mapping between Protobuf and internal models.
 *
 * @since 1.0.0
 */
public final class ModelMapper {
  private ModelMapper() {}

  public static CallbackConfig map(io.boomerang.proto.CallbackConfig config) {
    if (config == null || config.getEndpoint().isEmpty()) {
      return null;
    }
    return new CallbackConfig(
        CallbackConfig.Protocol.valueOf(config.getProtocol().name()), config.getEndpoint());
  }

  public static RetryPolicy map(io.boomerang.proto.RetryPolicy policy) {
    if (policy == null || policy.getMaxAttempts() == 0) {
      return null;
    }
    return new RetryPolicy(
        policy.getMaxAttempts(),
        RetryPolicy.BackoffStrategy.valueOf(policy.getStrategy().name()),
        policy.getIntervalMs(),
        policy.getMaxIntervalMs());
  }

  public static DLQPolicy map(io.boomerang.proto.DLQPolicy policy) {
    if (policy == null || policy.getDestination().isEmpty()) {
      return null;
    }
    return new DLQPolicy(policy.getDestination());
  }
}
