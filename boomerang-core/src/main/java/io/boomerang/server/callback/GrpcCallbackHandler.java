package io.boomerang.server.callback;

import com.google.protobuf.ByteString;
import io.boomerang.model.CallbackConfig;
import io.boomerang.proto.BoomerangCallbackGrpc;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;
import io.boomerang.proto.Status;
import io.boomerang.timer.TimerTask;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for gRPC callbacks.
 *
 * <p>Delivers task payloads using the standard gRPC {@link BoomerangCallbackGrpc} service. Manages
 * a pool of persistent {@link ManagedChannel}s for efficient delivery.
 *
 * @since 1.0.0
 */
public class GrpcCallbackHandler implements CallbackHandler {
  private static final Logger log = LoggerFactory.getLogger(GrpcCallbackHandler.class);

  private final Map<String, ManagedChannel> channelPool = new ConcurrentHashMap<>();
  private final long timeoutMs;

  /**
   * Constructs a new handler with a shared channel pool.
   *
   * @param timeoutMs the maximum time to wait for a gRPC response
   */
  public GrpcCallbackHandler(long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  @Override
  public void handle(TimerTask task, CallbackConfig config) throws CallbackException {
    ManagedChannel channel =
        channelPool.computeIfAbsent(
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
    channelPool
        .values()
        .forEach(
            channel -> {
              try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                log.warn("gRPC channel shutdown interrupted", e);
                Thread.currentThread().interrupt();
              }
            });
    channelPool.clear();
  }
}
