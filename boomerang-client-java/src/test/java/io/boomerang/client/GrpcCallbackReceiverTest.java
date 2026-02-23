package io.boomerang.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import io.boomerang.proto.BoomerangCallbackGrpc;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;
import io.boomerang.proto.Status;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GrpcCallbackReceiverTest {

  @Test
  void testReceiveGrpcCallback() throws Exception {
    int port = 12349;
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<CallbackRequest> receivedRequest = new AtomicReference<>();

    CallbackHandler handler =
        request -> {
          receivedRequest.set(request);
          latch.countDown();
          return CallbackResponse.newBuilder().setStatus(Status.OK).build();
        };

    try (GrpcCallbackReceiver receiver = new GrpcCallbackReceiver(port, handler)) {
      receiver.start();

      ManagedChannel channel =
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
      try {
        BoomerangCallbackGrpc.BoomerangCallbackBlockingStub stub =
            BoomerangCallbackGrpc.newBlockingStub(channel);
        CallbackRequest request =
            CallbackRequest.newBuilder()
                .setTaskId("task-grpc")
                .setPayload(ByteString.copyFromUtf8("grpc-data"))
                .build();

        CallbackResponse response = stub.onTaskExpired(request);
        assertEquals(Status.OK, response.getStatus());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "gRPC callback not received");
        assertEquals("task-grpc", receivedRequest.get().getTaskId());
      } finally {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void testClose() throws Exception {
    GrpcCallbackReceiver receiver = new GrpcCallbackReceiver(12358, r -> null);
    receiver.start();
    receiver.close();
    receiver.close();
  }
}
