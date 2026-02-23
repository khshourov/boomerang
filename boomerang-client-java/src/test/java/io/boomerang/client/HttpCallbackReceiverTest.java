package io.boomerang.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;
import io.boomerang.proto.Status;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpCallbackReceiverTest {

  @Test
  void testReceiveHttpCallback() throws Exception {
    int port = 12348;
    String path = "/callback";
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<CallbackRequest> receivedRequest = new AtomicReference<>();

    CallbackHandler handler =
        request -> {
          receivedRequest.set(request);
          latch.countDown();
          return CallbackResponse.newBuilder().setStatus(Status.OK).build();
        };

    try (HttpCallbackReceiver receiver = new HttpCallbackReceiver(port, path, handler)) {
      receiver.start();

      HttpClient client = HttpClient.newHttpClient();
      CallbackRequest request =
          CallbackRequest.newBuilder()
              .setTaskId("task-http")
              .setPayload(ByteString.copyFromUtf8("http-data"))
              .build();
      BoomerangEnvelope envelope =
          BoomerangEnvelope.newBuilder().setCallbackRequest(request).build();

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + port + path))
              .POST(HttpRequest.BodyPublishers.ofByteArray(envelope.toByteArray()))
              .build();

      HttpResponse<byte[]> response =
          client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

      assertEquals(200, response.statusCode());
      BoomerangEnvelope respEnvelope = BoomerangEnvelope.parseFrom(response.body());
      assertEquals(Status.OK, respEnvelope.getCallbackResponse().getStatus());

      assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP callback not received");
      assertEquals("task-http", receivedRequest.get().getTaskId());
    }
  }

  @Test
  void testClose() throws Exception {
    HttpCallbackReceiver receiver = new HttpCallbackReceiver(12356, "/", r -> null);
    receiver.start();
    receiver.close();
    receiver.close();
  }

  @Test
  void testNonPostRequest() throws Exception {
    int port = 12357;
    try (HttpCallbackReceiver receiver = new HttpCallbackReceiver(port, "/", r -> null)) {
      receiver.start();
      java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
      java.net.http.HttpRequest request =
          java.net.http.HttpRequest.newBuilder()
              .uri(java.net.URI.create("http://localhost:" + port + "/"))
              .GET()
              .build();
      java.net.http.HttpResponse<Void> response =
          client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
      assertEquals(405, response.statusCode());
    }
  }
}
