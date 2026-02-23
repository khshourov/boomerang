package io.boomerang.client;

import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP implementation of the {@link CallbackReceiver} using raw sockets.
 *
 * @since 0.1.0
 */
public class TcpCallbackReceiver implements CallbackReceiver {
  private static final Logger log = LoggerFactory.getLogger(TcpCallbackReceiver.class);
  private final int port;
  private final CallbackHandler handler;
  private ServerSocket serverSocket;
  private ExecutorService executorService;
  private volatile boolean running;

  public TcpCallbackReceiver(int port, CallbackHandler handler) {
    this.port = port;
    this.handler = handler;
  }

  @Override
  public void start() throws BoomerangException {
    try {
      serverSocket = new ServerSocket(port);
      executorService = Executors.newCachedThreadPool();
      running = true;
      log.info("TCP Callback Receiver started on port {}", port);

      executorService.submit(this::acceptConnections);
    } catch (IOException e) {
      throw new BoomerangException("Failed to start TCP receiver on port " + port, e);
    }
  }

  private void acceptConnections() {
    while (running) {
      try {
        Socket clientSocket = serverSocket.accept();
        executorService.submit(() -> handleConnection(clientSocket));
      } catch (IOException e) {
        if (running) {
          log.error("Error accepting connection", e);
        }
      }
    }
  }

  private void handleConnection(Socket clientSocket) {
    try (clientSocket;
        DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream())) {

      int length = inputStream.readInt();
      byte[] payload = new byte[length];
      inputStream.readFully(payload);

      BoomerangEnvelope envelope = BoomerangEnvelope.parseFrom(payload);
      if (envelope.hasCallbackRequest()) {
        CallbackRequest request = envelope.getCallbackRequest();
        CallbackResponse response = handler.onTaskExpired(request);

        BoomerangEnvelope responseEnvelope =
            BoomerangEnvelope.newBuilder().setCallbackResponse(response).build();
        byte[] responseBytes = responseEnvelope.toByteArray();
        outputStream.writeInt(responseBytes.length);
        outputStream.write(responseBytes);
        outputStream.flush();
      }
    } catch (IOException e) {
      log.error("Error handling TCP callback", e);
    }
  }

  @Override
  public void close() {
    running = false;
    try {
      if (serverSocket != null) {
        serverSocket.close();
      }
      if (executorService != null) {
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
      }
    } catch (IOException | InterruptedException e) {
      log.warn("Error closing TCP receiver", e);
    }
  }
}
