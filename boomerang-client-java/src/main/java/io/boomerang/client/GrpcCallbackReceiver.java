package io.boomerang.client;

import io.boomerang.proto.BoomerangCallbackGrpc;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC implementation of the {@link CallbackReceiver} using the generated gRPC classes.
 *
 * @since 0.1.0
 */
public class GrpcCallbackReceiver implements CallbackReceiver {
  private static final Logger log = LoggerFactory.getLogger(GrpcCallbackReceiver.class);
  private final int port;
  private final CallbackHandler handler;
  private Server server;

  public GrpcCallbackReceiver(int port, CallbackHandler handler) {
    this.port = port;
    this.handler = handler;
  }

  @Override
  public void start() throws BoomerangException {
    try {
      server = ServerBuilder.forPort(port).addService(new BoomerangCallbackImpl()).build();
      server.start();
      log.info("gRPC Callback Receiver started on port {}", port);
    } catch (IOException e) {
      throw new BoomerangException("Failed to start gRPC receiver on port " + port, e);
    }
  }

  private class BoomerangCallbackImpl extends BoomerangCallbackGrpc.BoomerangCallbackImplBase {
    @Override
    public void onTaskExpired(
        CallbackRequest request, StreamObserver<CallbackResponse> responseObserver) {
      try {
        CallbackResponse response = handler.onTaskExpired(request);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
      } catch (Exception e) {
        log.error("Error handling gRPC callback", e);
        responseObserver.onError(e);
      }
    }
  }

  @Override
  public void close() {
    if (server != null) {
      server.shutdown();
      try {
        server.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        log.warn("Error terminating gRPC receiver", e);
      }
    }
  }
}
