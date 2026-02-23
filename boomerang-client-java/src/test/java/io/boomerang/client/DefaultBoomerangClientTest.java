package io.boomerang.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import io.boomerang.proto.AuthResponse;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.RegistrationResponse;
import io.boomerang.proto.Status;
import io.boomerang.proto.Task;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultBoomerangClientTest {
  private DefaultBoomerangClient client;
  private Socket mockSocket;
  private ByteArrayOutputStream clientOutput;

  // A simple list to hold responses that will be fed to the client
  private List<byte[]> responses;

  @BeforeEach
  void setUp() throws IOException {
    mockSocket = mock(Socket.class);
    clientOutput = new ByteArrayOutputStream();
    responses = new ArrayList<>();

    when(mockSocket.getOutputStream()).thenReturn(clientOutput);
    // Custom InputStream that reads from our list of responses
    InputStream inputStream =
        new InputStream() {
          private ByteArrayInputStream current = null;
          private int index = 0;

          @Override
          public int read() throws IOException {
            if (current == null || current.available() == 0) {
              if (index < responses.size()) {
                current = new ByteArrayInputStream(responses.get(index++));
              } else {
                return -1;
              }
            }
            return current.read();
          }
        };
    when(mockSocket.getInputStream()).thenReturn(inputStream);

    client = new DefaultBoomerangClient("localhost", 12345, mockSocket);
  }

  private void addResponse(BoomerangEnvelope envelope) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bos);
    byte[] b = envelope.toByteArray();
    dos.writeInt(b.length);
    dos.write(b);
    responses.add(bos.toByteArray());
  }

  @Test
  void testLoginSuccess() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("test-session").build())
            .build());

    client.connect();
    client.login("client-1", "pass-1");

    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(clientOutput.toByteArray()));
    int len = dis.readInt();
    byte[] b = new byte[len];
    dis.readFully(b);
    BoomerangEnvelope req = BoomerangEnvelope.parseFrom(b);
    assertEquals("client-1", req.getAuthHandshake().getClientId());
  }

  @Test
  void testLoginFailureWithStatus() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder()
                    .setStatus(Status.SESSION_EXPIRED)
                    .setErrorMessage("expired")
                    .build())
            .build());

    client.connect();
    BoomerangException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            BoomerangException.class, () -> client.login("u", "p"));
    assertTrue(ex.getStatus().isPresent());
    assertEquals(Status.SESSION_EXPIRED, ex.getStatus().get());
  }

  @Test
  void testRegisterTaskFailure() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("s").build())
            .build());
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setRegistrationResponse(
                RegistrationResponse.newBuilder()
                    .setStatus(Status.INVALID_REQUEST)
                    .setErrorMessage("bad")
                    .build())
            .build());

    client.connect();
    client.login("u", "p");
    BoomerangException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            BoomerangException.class, () -> client.register(Task.getDefaultInstance()));
    assertEquals(Status.INVALID_REQUEST, ex.getStatus().get());
  }

  @Test
  void testRegisterTask() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("s").build())
            .build());
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setRegistrationResponse(
                RegistrationResponse.newBuilder().setStatus(Status.OK).setTaskId("t-123").build())
            .build());

    client.connect();
    client.login("u", "p");
    Task task = Task.newBuilder().setPayload(ByteString.copyFromUtf8("h")).build();
    RegistrationResponse response = client.register(task);

    assertEquals("t-123", response.getTaskId());
  }

  @Test
  void testCancelTaskSuccess() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("s").build())
            .build());
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setCancellationResponse(
                io.boomerang.proto.CancellationResponse.newBuilder().setStatus(Status.OK).build())
            .build());

    client.connect();
    client.login("u", "p");
    assertTrue(client.cancel("t1"));
  }

  @Test
  void testCancelTaskFailure() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("s").build())
            .build());
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setCancellationResponse(
                io.boomerang.proto.CancellationResponse.newBuilder()
                    .setStatus(Status.UNAUTHORIZED)
                    .build())
            .build());

    client.connect();
    client.login("u", "p");
    BoomerangException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            BoomerangException.class, () -> client.cancel("t1"));
    assertEquals(Status.UNAUTHORIZED, ex.getStatus().get());
  }

  @Test
  void testGetTaskFailure() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("s").build())
            .build());
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setGetTaskResponse(
                io.boomerang.proto.GetTaskResponse.newBuilder()
                    .setStatus(Status.INVALID_REQUEST)
                    .build())
            .build());

    client.connect();
    client.login("u", "p");
    BoomerangException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            BoomerangException.class, () -> client.getTask("t1"));
    assertEquals(Status.INVALID_REQUEST, ex.getStatus().get());
  }

  @Test
  void testListTasksFailure() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("s").build())
            .build());
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setListTasksResponse(
                io.boomerang.proto.ListTasksResponse.newBuilder()
                    .setStatus(Status.SESSION_EXPIRED)
                    .build())
            .build());

    client.connect();
    client.login("u", "p");
    BoomerangException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            BoomerangException.class,
            () -> client.listTasks(io.boomerang.proto.ListTasksRequest.newBuilder().build()));
    assertEquals(Status.SESSION_EXPIRED, ex.getStatus().get());
  }

  @Test
  void testGetTask() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("s").build())
            .build());
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setGetTaskResponse(
                io.boomerang.proto.GetTaskResponse.newBuilder()
                    .setStatus(Status.OK)
                    .setTask(io.boomerang.proto.TaskDetails.newBuilder().setTaskId("t1").build())
                    .build())
            .build());

    client.connect();
    client.login("u", "p");
    assertEquals("t1", client.getTask("t1").getTask().getTaskId());
  }

  @Test
  void testListTasks() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("s").build())
            .build());
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setListTasksResponse(
                io.boomerang.proto.ListTasksResponse.newBuilder()
                    .setStatus(Status.OK)
                    .addTasks(io.boomerang.proto.TaskDetails.newBuilder().setTaskId("t1").build())
                    .build())
            .build());

    client.connect();
    client.login("u", "p");
    assertEquals(
        1,
        client.listTasks(io.boomerang.proto.ListTasksRequest.newBuilder().build()).getTasksCount());
  }

  @Test
  void testUnexpectedResponse() throws Exception {
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(
                AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("s").build())
            .build());
    addResponse(
        BoomerangEnvelope.newBuilder()
            .setAuthResponse(AuthResponse.newBuilder().setStatus(Status.OK).build())
            .build());

    client.connect();
    client.login("u", "p");
    org.junit.jupiter.api.Assertions.assertThrows(
        BoomerangException.class, () -> client.register(Task.getDefaultInstance()));
  }

  @Test
  void testClose() throws Exception {
    client.connect();
    client.close();
    // Subsequent calls to sendAndReceive should fail because socket is effectively "closed" in mock
    // behavior
    // but here we just check our own logic
    org.junit.jupiter.api.Assertions.assertThrows(
        BoomerangException.class, () -> client.login("u", "p"));
  }

  @Test
  void testConnectFailure() throws IOException {
    Socket badSocket = mock(Socket.class);
    when(badSocket.getInputStream()).thenThrow(new IOException("fail"));
    DefaultBoomerangClient badClient = new DefaultBoomerangClient("h", 1, badSocket);
    org.junit.jupiter.api.Assertions.assertThrows(BoomerangException.class, badClient::connect);
  }
}
