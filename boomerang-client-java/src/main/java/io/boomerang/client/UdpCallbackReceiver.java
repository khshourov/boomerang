package io.boomerang.client;

import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CallbackRequest;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UDP implementation of the {@link CallbackReceiver} using datagram sockets.
 *
 * @since 0.1.0
 */
public class UdpCallbackReceiver implements CallbackReceiver {
  private static final Logger log = LoggerFactory.getLogger(UdpCallbackReceiver.class);
  private final int port;
  private final CallbackHandler handler;
  private DatagramSocket datagramSocket;
  private ExecutorService executorService;
  private volatile boolean running;

  public UdpCallbackReceiver(int port, CallbackHandler handler) {
    this.port = port;
    this.handler = handler;
  }

  @Override
  public void start() throws BoomerangException {
    try {
      datagramSocket = new DatagramSocket(port);
      executorService = Executors.newCachedThreadPool();
      running = true;
      log.info("UDP Callback Receiver started on port {}", port);

      executorService.submit(this::receivePackets);
    } catch (IOException e) {
      throw new BoomerangException("Failed to start UDP receiver on port " + port, e);
    }
  }

  private void receivePackets() {
    byte[] buffer = new byte[65535];
    while (running) {
      try {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        datagramSocket.receive(packet);
        byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
        executorService.submit(() -> handlePacket(data));
      } catch (IOException e) {
        if (running) {
          log.error("Error receiving UDP packet", e);
        }
      }
    }
  }

  private void handlePacket(byte[] data) {
    try {
      BoomerangEnvelope envelope = BoomerangEnvelope.parseFrom(data);
      if (envelope.hasCallbackRequest()) {
        CallbackRequest request = envelope.getCallbackRequest();
        handler.onTaskExpired(request);
      }
    } catch (IOException e) {
      log.error("Error handling UDP callback", e);
    }
  }

  @Override
  public void close() {
    running = false;
    if (datagramSocket != null) {
      datagramSocket.close();
    }
    if (executorService != null) {
      executorService.shutdown();
      try {
        executorService.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        log.warn("Error terminating UDP receiver", e);
      }
    }
  }
}
