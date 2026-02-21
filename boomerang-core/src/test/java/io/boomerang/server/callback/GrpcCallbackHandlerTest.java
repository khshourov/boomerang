package io.boomerang.server.callback;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.boomerang.model.CallbackConfig;
import io.boomerang.proto.BoomerangCallbackGrpc;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;
import io.boomerang.proto.Status;
import io.boomerang.timer.TimerTask;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrpcCallbackHandlerTest {

  private Server server;
  private GrpcCallbackHandler handler;
  private String serverEndpoint;
  private volatile Status serverStatus = Status.OK;

  @BeforeEach
  void setUp() throws IOException {
    // Start a real gRPC server on a random local port
    server = ServerBuilder.forPort(0).addService(new MockCallbackService()).build().start();

    int port = server.getPort();
    serverEndpoint = "localhost:" + port;

    handler = new GrpcCallbackHandler(2000);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    if (handler != null) {
      handler.shutdown();
    }
  }

  @Test
  void shouldDeliverSuccessfully() {
    serverStatus = Status.OK;
    TimerTask task = new TimerTask("task-1", "client-1", 100, "hello".getBytes(), 0, () -> {});
    CallbackConfig config = new CallbackConfig(CallbackConfig.Protocol.GRPC, serverEndpoint);

    assertThatCode(() -> handler.handle(task, config)).doesNotThrowAnyException();
  }

  @Test
  void shouldThrowWhenStatusIsNotOk() {
    serverStatus = Status.ERROR;
    TimerTask task = new TimerTask("task-1", "client-1", 100, "hello".getBytes(), 0, () -> {});
    CallbackConfig config = new CallbackConfig(CallbackConfig.Protocol.GRPC, serverEndpoint);

    assertThatThrownBy(() -> handler.handle(task, config))
        .isInstanceOf(CallbackException.class)
        .hasMessageContaining("ERROR");
  }

  private class MockCallbackService extends BoomerangCallbackGrpc.BoomerangCallbackImplBase {
    @Override
    public void onTaskExpired(
        CallbackRequest request, StreamObserver<CallbackResponse> responseObserver) {
      CallbackResponse response = CallbackResponse.newBuilder().setStatus(serverStatus).build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }
}
