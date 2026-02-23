package io.boomerang.client;

import io.boomerang.proto.AuthHandshake;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CancellationRequest;
import io.boomerang.proto.ClientDeregistrationRequest;
import io.boomerang.proto.ClientDeregistrationResponse;
import io.boomerang.proto.ClientRegistrationRequest;
import io.boomerang.proto.ClientRegistrationResponse;
import io.boomerang.proto.GetTaskRequest;
import io.boomerang.proto.GetTaskResponse;
import io.boomerang.proto.ListTasksRequest;
import io.boomerang.proto.ListTasksResponse;
import io.boomerang.proto.RegistrationResponse;
import io.boomerang.proto.Status;
import io.boomerang.proto.Task;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standard implementation of the {@link BoomerangClient} using standard Java sockets.
 *
 * @since 0.1.0
 */
public class DefaultBoomerangClient implements BoomerangClient {
  private static final Logger log = LoggerFactory.getLogger(DefaultBoomerangClient.class);
  private final String host;
  private final int port;
  private Socket socket;
  private DataInputStream inputStream;
  private DataOutputStream outputStream;
  private String sessionId;

  public DefaultBoomerangClient(String host, int port) {
    this.host = host;
    this.port = port;
  }

  /** Constructor for testing. */
  DefaultBoomerangClient(String host, int port, Socket socket) {
    this.host = host;
    this.port = port;
    this.socket = socket;
  }

  @Override
  public void connect() throws BoomerangException {
    try {
      if (socket == null) {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
      }
      socket.setSoTimeout(5000);
      inputStream = new DataInputStream(socket.getInputStream());
      outputStream = new DataOutputStream(socket.getOutputStream());
      log.debug("Connected to {}:{}", host, port);
    } catch (IOException e) {
      throw new BoomerangException("Failed to connect to " + host + ":" + port, e);
    }
  }

  @Override
  public void login(String clientId, String password) throws BoomerangException {
    AuthHandshake handshake =
        AuthHandshake.newBuilder().setClientId(clientId).setPassword(password).build();
    BoomerangEnvelope envelope = BoomerangEnvelope.newBuilder().setAuthHandshake(handshake).build();

    BoomerangEnvelope response = sendAndReceive(envelope);
    if (response.hasAuthResponse()) {
      checkStatus(
          response.getAuthResponse().getStatus(), response.getAuthResponse().getErrorMessage());
      this.sessionId = response.getAuthResponse().getSessionId();
      log.debug("Successfully logged in with clientId: {}", clientId);
    } else {
      throw new BoomerangException("Unexpected response type: " + response.getPayloadCase());
    }
  }

  @Override
  public RegistrationResponse register(Task task) throws BoomerangException {
    BoomerangEnvelope envelope = createEnvelope().toBuilder().setRegistrationRequest(task).build();
    BoomerangEnvelope response = sendAndReceive(envelope);
    if (response.hasRegistrationResponse()) {
      checkStatus(
          response.getRegistrationResponse().getStatus(),
          response.getRegistrationResponse().getErrorMessage());
      return response.getRegistrationResponse();
    }
    throw new BoomerangException("Unexpected response type: " + response.getPayloadCase());
  }

  @Override
  public boolean cancel(String taskId) throws BoomerangException {
    CancellationRequest request = CancellationRequest.newBuilder().setTaskId(taskId).build();
    BoomerangEnvelope envelope =
        createEnvelope().toBuilder().setCancellationRequest(request).build();
    BoomerangEnvelope response = sendAndReceive(envelope);
    if (response.hasCancellationResponse()) {
      checkStatus(
          response.getCancellationResponse().getStatus(),
          response.getCancellationResponse().getErrorMessage());
      return response.getCancellationResponse().getStatus() == Status.OK;
    }
    throw new BoomerangException("Unexpected response type: " + response.getPayloadCase());
  }

  @Override
  public GetTaskResponse getTask(String taskId) throws BoomerangException {
    GetTaskRequest request = GetTaskRequest.newBuilder().setTaskId(taskId).build();
    BoomerangEnvelope envelope = createEnvelope().toBuilder().setGetTaskRequest(request).build();
    BoomerangEnvelope response = sendAndReceive(envelope);
    if (response.hasGetTaskResponse()) {
      checkStatus(
          response.getGetTaskResponse().getStatus(),
          response.getGetTaskResponse().getErrorMessage());
      return response.getGetTaskResponse();
    }
    throw new BoomerangException("Unexpected response type: " + response.getPayloadCase());
  }

  @Override
  public ListTasksResponse listTasks(ListTasksRequest request) throws BoomerangException {
    BoomerangEnvelope envelope = createEnvelope().toBuilder().setListTasksRequest(request).build();
    BoomerangEnvelope response = sendAndReceive(envelope);
    if (response.hasListTasksResponse()) {
      checkStatus(
          response.getListTasksResponse().getStatus(),
          response.getListTasksResponse().getErrorMessage());
      return response.getListTasksResponse();
    }
    throw new BoomerangException("Unexpected response type: " + response.getPayloadCase());
  }

  @Override
  public ClientRegistrationResponse registerClient(ClientRegistrationRequest request)
      throws BoomerangException {
    BoomerangEnvelope envelope =
        createEnvelope().toBuilder().setClientRegistration(request).build();
    BoomerangEnvelope response = sendAndReceive(envelope);
    if (response.hasClientRegistrationResponse()) {
      checkStatus(
          response.getClientRegistrationResponse().getStatus(),
          response.getClientRegistrationResponse().getErrorMessage());
      return response.getClientRegistrationResponse();
    }
    throw new BoomerangException("Unexpected response type: " + response.getPayloadCase());
  }

  @Override
  public ClientDeregistrationResponse deregisterClient(ClientDeregistrationRequest request)
      throws BoomerangException {
    BoomerangEnvelope envelope =
        createEnvelope().toBuilder().setClientDeregistration(request).build();
    BoomerangEnvelope response = sendAndReceive(envelope);
    if (response.hasClientDeregistrationResponse()) {
      checkStatus(
          response.getClientDeregistrationResponse().getStatus(),
          response.getClientDeregistrationResponse().getErrorMessage());
      return response.getClientDeregistrationResponse();
    }
    throw new BoomerangException("Unexpected response type: " + response.getPayloadCase());
  }

  private void checkStatus(Status status, String errorMessage) {
    if (status != Status.OK) {
      throw new BoomerangException(status, errorMessage);
    }
  }

  private BoomerangEnvelope createEnvelope() {
    if (sessionId == null) {
      throw new BoomerangException("Client not logged in");
    }
    return BoomerangEnvelope.newBuilder().setSessionId(sessionId).build();
  }

  private synchronized BoomerangEnvelope sendAndReceive(BoomerangEnvelope envelope)
      throws BoomerangException {
    try {
      byte[] payload = envelope.toByteArray();
      outputStream.writeInt(payload.length);
      outputStream.write(payload);
      outputStream.flush();

      int length = inputStream.readInt();
      byte[] responsePayload = new byte[length];
      inputStream.readFully(responsePayload);
      return BoomerangEnvelope.parseFrom(responsePayload);
    } catch (IOException e) {
      throw new BoomerangException("Communication error", e);
    }
  }

  @Override
  public void close() {
    try {
      if (socket != null) {
        socket.close();
      }
    } catch (IOException e) {
      log.warn("Error closing socket", e);
    }
  }
}
