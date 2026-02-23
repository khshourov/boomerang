package io.boomerang.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP implementation of the {@link CallbackReceiver} using the built-in Sun HTTP server.
 *
 * @since 0.1.0
 */
public class HttpCallbackReceiver implements CallbackReceiver {
  private static final Logger log = LoggerFactory.getLogger(HttpCallbackReceiver.class);
  private final int port;
  private final String path;
  private final CallbackHandler handler;
  private HttpServer server;
  private ExecutorService executorService;

  public HttpCallbackReceiver(int port, String path, CallbackHandler handler) {
    this.port = port;
    this.path = path.startsWith("/") ? path : "/" + path;
    this.handler = handler;
  }

  @Override
  public void start() throws BoomerangException {
    try {
      server = HttpServer.create(new InetSocketAddress(port), 0);
      executorService = Executors.newCachedThreadPool();
      server.setExecutor(executorService);
      server.createContext(path, new InternalHandler());
      server.start();
      log.info("HTTP Callback Receiver started on port {} at path {}", port, path);
    } catch (IOException e) {
      throw new BoomerangException("Failed to start HTTP receiver on port " + port, e);
    }
  }

  private class InternalHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        return;
      }

      try (InputStream is = exchange.getRequestBody();
          OutputStream os = exchange.getResponseBody()) {

        byte[] inputData = is.readAllBytes();
        BoomerangEnvelope envelope = BoomerangEnvelope.parseFrom(inputData);

        if (envelope.hasCallbackRequest()) {
          CallbackRequest request = envelope.getCallbackRequest();
          CallbackResponse response = handler.onTaskExpired(request);

          BoomerangEnvelope responseEnvelope =
              BoomerangEnvelope.newBuilder().setCallbackResponse(response).build();
          byte[] responseBytes = responseEnvelope.toByteArray();

          exchange.sendResponseHeaders(200, responseBytes.length);
          os.write(responseBytes);
          os.flush();
        } else {
          exchange.sendResponseHeaders(400, -1); // Bad Request
        }
      } catch (Exception e) {
        log.error("Error handling HTTP callback", e);
        exchange.sendResponseHeaders(500, -1); // Internal Server Error
      }
    }
  }

  @Override
  public void close() {
    if (server != null) {
      server.stop(0);
    }
    if (executorService != null) {
      executorService.shutdown();
      try {
        executorService.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        log.warn("Error terminating HTTP receiver", e);
      }
    }
  }
}
