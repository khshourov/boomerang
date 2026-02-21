package io.boomerang.server.callback;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.boomerang.model.CallbackConfig;
import io.boomerang.timer.TimerTask;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpCallbackHandlerTest {

  private HttpServer server;
  private HttpCallbackHandler handler;
  private int port;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.start();
    port = server.getAddress().getPort();
    handler = new HttpCallbackHandler(Duration.ofSeconds(2));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void shouldDeliverSuccessfully() {
    server.createContext(
        "/callback",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });

    TimerTask task = new TimerTask("task-1", "client-1", 100, "hello".getBytes(), 0, () -> {});
    CallbackConfig config =
        new CallbackConfig(CallbackConfig.Protocol.HTTP, "http://localhost:" + port + "/callback");

    assertThatCode(() -> handler.handle(task, config)).doesNotThrowAnyException();
  }

  @Test
  void shouldThrowWhenStatusIsNotOk() {
    server.createContext(
        "/fail",
        exchange -> {
          String response = "Internal Server Error";
          exchange.sendResponseHeaders(500, response.length());
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
          }
          exchange.close();
        });

    TimerTask task = new TimerTask("task-1", "client-1", 100, "hello".getBytes(), 0, () -> {});
    CallbackConfig config =
        new CallbackConfig(CallbackConfig.Protocol.HTTP, "http://localhost:" + port + "/fail");

    assertThatThrownBy(() -> handler.handle(task, config))
        .isInstanceOf(CallbackException.class)
        .hasMessageContaining("500");
  }
}
