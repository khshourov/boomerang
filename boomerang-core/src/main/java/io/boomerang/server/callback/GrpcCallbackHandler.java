package io.boomerang.server.callback;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.google.protobuf.ByteString;
import io.boomerang.model.CallbackConfig;
import io.boomerang.proto.BoomerangCallbackGrpc;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;
import io.boomerang.proto.Status;
import io.boomerang.timer.TimerTask;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for gRPC callbacks with channel pooling and eviction.
 *
 * <p>Delivers task payloads using the standard gRPC {@link BoomerangCallbackGrpc} service. Uses
 * {@link Caffeine} to manage a pool of persistent {@link ManagedChannel}s with eviction logic.
 *
 * @since 1.0.0
 */
public class GrpcCallbackHandler implements CallbackHandler {
  private static final Logger log = LoggerFactory.getLogger(GrpcCallbackHandler.class);

  private final Cache<String, ManagedChannel> channelPool;
  private final long timeoutMs;

  /**
   * Constructs a new handler with a shared channel pool and a custom eviction listener.
   *
   * @param timeoutMs the maximum time to wait for a gRPC response
   * @param maxChannels maximum number of channels to keep in the pool
   * @param idleTimeoutMs idle time before a channel is evicted
   * @param onEvict listener called when a channel is evicted; can be {@code null}
   */
  public GrpcCallbackHandler(
      long timeoutMs,
      int maxChannels,
      long idleTimeoutMs,
      java.util.function.Consumer<String> onEvict) {
    this.timeoutMs = timeoutMs;
    this.channelPool =
        Caffeine.newBuilder()
            .maximumSize(maxChannels)
            .expireAfterAccess(idleTimeoutMs, TimeUnit.MILLISECONDS)
            .removalListener(
                (String endpoint, ManagedChannel channel, RemovalCause cause) -> {
                  log.info("Closing gRPC channel for endpoint {} due to {}", endpoint, cause);
                  if (onEvict != null) {
                    onEvict.accept(endpoint);
                  }
                  if (channel != null) {
                    try {
                      channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                      log.warn("gRPC channel shutdown interrupted for {}", endpoint, e);
                      Thread.currentThread().interrupt();
                    }
                  }
                })
            .build();
  }

  /**
   * Constructs a new handler with a shared channel pool.
   *
   * @param timeoutMs the maximum time to wait for a gRPC response
   * @param maxChannels maximum number of channels to keep in the pool
   * @param idleTimeoutMs idle time before a channel is evicted
   */
  public GrpcCallbackHandler(long timeoutMs, int maxChannels, long idleTimeoutMs) {
    this(timeoutMs, maxChannels, idleTimeoutMs, null);
  }

  @Override
  public void handle(TimerTask task, CallbackConfig config) throws CallbackException {
    ManagedChannel channel =
        channelPool.get(
            config.endpoint(),
            endpoint -> {
              log.info("Creating new gRPC channel for endpoint: {}", endpoint);
              return ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build();
            });

    BoomerangCallbackGrpc.BoomerangCallbackBlockingStub stub =
        BoomerangCallbackGrpc.newBlockingStub(channel)
            .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);

    CallbackRequest request =
        CallbackRequest.newBuilder()
            .setTaskId(task.getTaskId())
            .setPayload(
                task.getPayload() != null
                    ? ByteString.copyFrom(task.getPayload())
                    : ByteString.EMPTY)
            .build();

    log.debug("Sending gRPC callback to {}", config.endpoint());
    try {
      CallbackResponse response = stub.onTaskExpired(request);

      if (response.getStatus() != Status.OK) {
        throw new CallbackException(
            "gRPC callback failed with status "
                + response.getStatus()
                + ": "
                + response.getErrorMessage());
      }
    } catch (Exception e) {
      if (e instanceof CallbackException exception) {
        throw exception;
      }
      throw new CallbackException("gRPC callback execution failed: " + e.getMessage(), e);
    }
  }

  @Override
  public CallbackConfig.Protocol getProtocol() {
    return CallbackConfig.Protocol.GRPC;
  }

  @Override
  public void shutdown() {
    log.info("Shutting down gRPC channel pool...");
    channelPool.invalidateAll();
    channelPool.cleanUp();
  }

  /**
   * Performs any pending maintenance operations needed by the cache.
   *
   * <p>Used primarily for testing eviction logic.
   */
  void cleanUp() {
    channelPool.cleanUp();
  }
}
