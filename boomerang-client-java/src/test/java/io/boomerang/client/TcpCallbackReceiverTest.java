package io.boomerang.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;
import io.boomerang.proto.Status;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TcpCallbackReceiverTest {

  @Test
  void testReceiveCallback() throws Exception {
    int port = 12346;
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<CallbackRequest> receivedRequest = new AtomicReference<>();

    CallbackHandler handler =
        request -> {
          receivedRequest.set(request);
          latch.countDown();
          return CallbackResponse.newBuilder().setStatus(Status.OK).build();
        };

    try (TcpCallbackReceiver receiver = new TcpCallbackReceiver(port, handler)) {
      receiver.start();

      // Simulate server sending a callback
      try (Socket socket = new Socket("localhost", port);
          DataOutputStream out = new DataOutputStream(socket.getOutputStream());
          DataInputStream in = new DataInputStream(socket.getInputStream())) {

        CallbackRequest request =
            CallbackRequest.newBuilder()
                .setTaskId("task-1")
                .setPayload(ByteString.copyFromUtf8("data"))
                .build();
        BoomerangEnvelope envelope =
            BoomerangEnvelope.newBuilder().setCallbackRequest(request).build();

        byte[] data = envelope.toByteArray();
        out.writeInt(data.length);
        out.write(data);
        out.flush();

        // Read response
        int length = in.readInt();
        byte[] respData = new byte[length];
        in.readFully(respData);
        BoomerangEnvelope respEnvelope = BoomerangEnvelope.parseFrom(respData);

        assertEquals(Status.OK, respEnvelope.getCallbackResponse().getStatus());
      }

      assertTrue(latch.await(5, TimeUnit.SECONDS), "Callback not received in time");
      assertEquals("task-1", receivedRequest.get().getTaskId());
    }
  }

  @Test
  void testClose() throws Exception {
    TcpCallbackReceiver receiver = new TcpCallbackReceiver(12354, r -> null);
    receiver.start();
    receiver.close();
    // Verify it doesn't throw on double close
    receiver.close();
  }
}
