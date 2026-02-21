package io.boomerang.server.callback;

import io.boomerang.model.CallbackConfig;
import io.boomerang.timer.TimerTask;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for HTTP/Webhook callbacks.
 *
 * <p>Uses the standard Java {@link HttpClient} to deliver task payloads to remote endpoints via
 * POST requests.
 *
 * @since 1.0.0
 */
public class HttpCallbackHandler implements CallbackHandler {
  private static final Logger log = LoggerFactory.getLogger(HttpCallbackHandler.class);

  private final HttpClient httpClient;

  /**
   * Constructs a new handler with a default HTTP client.
   *
   * @param timeout the default connection and request timeout; must be non-null
   */
  public HttpCallbackHandler(Duration timeout) {
    this.httpClient =
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).connectTimeout(timeout).build();
  }

  @Override
  public void handle(TimerTask task, CallbackConfig config) throws CallbackException {
    byte[] payload = task.getPayload();
    if (payload == null) {
      payload = new byte[0];
    }

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(config.endpoint()))
            .header("Content-Type", "application/octet-stream")
            .header("X-Boomerang-Task-Id", task.getTaskId())
            .header("X-Boomerang-Client-Id", task.getClientId())
            .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
            .build();

    log.debug("Sending HTTP POST to {}", config.endpoint());

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new CallbackException(
            "HTTP callback failed with status " + response.statusCode() + ": " + response.body());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CallbackException("TCP callback connection interrupted", e);
    } catch (IOException e) {
      throw new CallbackException("HTTP callback failed: " + e.getMessage(), e);
    }
  }

  @Override
  public CallbackConfig.Protocol getProtocol() {
    return CallbackConfig.Protocol.HTTP;
  }
}
