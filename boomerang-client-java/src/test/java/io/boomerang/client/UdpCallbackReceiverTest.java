package io.boomerang.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CallbackRequest;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UdpCallbackReceiverTest {

  @Test
  void testReceiveUdpCallback() throws Exception {
    int port = 12347;
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<CallbackRequest> receivedRequest = new AtomicReference<>();

    CallbackHandler handler =
        request -> {
          receivedRequest.set(request);
          latch.countDown();
          return null; // Not used for UDP
        };

    try (UdpCallbackReceiver receiver = new UdpCallbackReceiver(port, handler)) {
      receiver.start();

      try (DatagramSocket socket = new DatagramSocket()) {
        CallbackRequest request =
            CallbackRequest.newBuilder()
                .setTaskId("task-udp")
                .setPayload(ByteString.copyFromUtf8("udp-data"))
                .build();
        BoomerangEnvelope envelope =
            BoomerangEnvelope.newBuilder().setCallbackRequest(request).build();

        byte[] data = envelope.toByteArray();
        DatagramPacket packet =
            new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), port);
        socket.send(packet);
      }

      assertTrue(latch.await(5, TimeUnit.SECONDS), "UDP callback not received");
      assertEquals("task-udp", receivedRequest.get().getTaskId());
    }
  }

  @Test
  void testClose() throws Exception {
    UdpCallbackReceiver receiver = new UdpCallbackReceiver(12355, r -> null);
    receiver.start();
    receiver.close();
    receiver.close();
  }
}
