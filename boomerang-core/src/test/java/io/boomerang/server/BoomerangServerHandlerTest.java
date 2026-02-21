package io.boomerang.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.boomerang.auth.AuthService;
import io.boomerang.model.Session;
import io.boomerang.proto.AuthHandshake;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.Status;
import io.boomerang.session.SessionManager;
import io.boomerang.timer.Timer;
import io.boomerang.timer.TimerTask;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoomerangServerHandlerTest {
  private AuthService authService;
  private SessionManager sessionManager;
  private Timer timer;
  private EmbeddedChannel channel;

  @BeforeEach
  void setUp() {
    authService = mock(AuthService.class);
    sessionManager = mock(SessionManager.class);
    timer = mock(Timer.class);
    BoomerangServerHandler handler = new BoomerangServerHandler(authService, sessionManager, timer);
    channel = new EmbeddedChannel(handler);
  }

  @Test
  void testHandleAuthHandshakeSuccess() {
    AuthHandshake handshake =
        AuthHandshake.newBuilder().setClientId("test-client").setPassword("password").build();
    BoomerangEnvelope envelope = BoomerangEnvelope.newBuilder().setAuthHandshake(handshake).build();

    Session session =
        new Session("session-id", "test-client", null, null, null, Instant.now().plusSeconds(3600));
    when(authService.authenticate("test-client", "password")).thenReturn(Optional.of(session));

    channel.writeInbound(envelope);

    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getPayloadCase()).isEqualTo(BoomerangEnvelope.PayloadCase.AUTH_RESPONSE);
    assertThat(response.getAuthResponse().getStatus()).isEqualTo(Status.OK);
    assertThat(response.getAuthResponse().getSessionId()).isEqualTo("session-id");
  }

  @Test
  void testHandleAuthHandshakeFailure() {
    AuthHandshake handshake =
        AuthHandshake.newBuilder().setClientId("test-client").setPassword("wrong").build();
    BoomerangEnvelope envelope = BoomerangEnvelope.newBuilder().setAuthHandshake(handshake).build();

    when(authService.authenticate("test-client", "wrong")).thenReturn(Optional.empty());

    channel.writeInbound(envelope);

    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getAuthResponse().getStatus()).isEqualTo(Status.UNAUTHORIZED);
  }

  @Test
  void testHandleRegistrationSuccess() {
    String sessionId = "valid-session";
    when(sessionManager.isValid(sessionId)).thenReturn(true);
    when(sessionManager.getClientId(sessionId)).thenReturn("test-client");

    io.boomerang.proto.Task taskRequest =
        io.boomerang.proto.Task.newBuilder()
            .setPayload(com.google.protobuf.ByteString.copyFromUtf8("hello"))
            .setDelayMs(1000)
            .build();
    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setRegistrationRequest(taskRequest)
            .build();

    channel.writeInbound(envelope);

    verify(timer).add(any(TimerTask.class));
    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getRegistrationResponse().getStatus()).isEqualTo(Status.OK);
    assertThat(response.getRegistrationResponse().getTaskId()).isNotEmpty();
  }

  @Test
  void testHandleRegistrationUnauthorized() {
    String sessionId = "invalid-session";
    when(sessionManager.isValid(sessionId)).thenReturn(false);

    io.boomerang.proto.Task taskRequest = io.boomerang.proto.Task.newBuilder().build();
    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setRegistrationRequest(taskRequest)
            .build();

    channel.writeInbound(envelope);

    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getRegistrationResponse().getStatus()).isEqualTo(Status.UNAUTHORIZED);
  }

  @Test
  void testHandleCancellationSuccess() {
    String sessionId = "valid-session";
    when(sessionManager.isValid(sessionId)).thenReturn(true);

    io.boomerang.proto.CancellationRequest cancelRequest =
        io.boomerang.proto.CancellationRequest.newBuilder().setTaskId("task-1").build();
    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setCancellationRequest(cancelRequest)
            .build();

    channel.writeInbound(envelope);

    verify(timer).cancel("task-1");
    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getCancellationResponse().getStatus()).isEqualTo(Status.OK);
  }

  @Test
  void testHandleCancellationUnauthorized() {
    String sessionId = "invalid-session";
    when(sessionManager.isValid(sessionId)).thenReturn(false);

    io.boomerang.proto.CancellationRequest cancelRequest =
        io.boomerang.proto.CancellationRequest.newBuilder().setTaskId("task-1").build();
    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setCancellationRequest(cancelRequest)
            .build();

    channel.writeInbound(envelope);

    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getCancellationResponse().getStatus()).isEqualTo(Status.UNAUTHORIZED);
  }

  @Test
  void testHandleSessionRefreshSuccess() {
    String sessionId = "valid-session";
    Session session =
        new Session(sessionId, "client-1", null, null, null, Instant.now().plusSeconds(3600));
    when(sessionManager.refreshSession(sessionId)).thenReturn(Optional.of(session));

    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setSessionRefresh(io.boomerang.proto.SessionRefreshRequest.newBuilder().build())
            .build();

    channel.writeInbound(envelope);

    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getSessionRefreshResponse().getStatus()).isEqualTo(Status.OK);
    assertThat(response.getSessionRefreshResponse().getNewExpiresAtMs())
        .isEqualTo(session.expiresAt().toEpochMilli());
  }

  @Test
  void testHandleSessionRefreshUnauthorized() {
    String sessionId = "invalid-session";
    when(sessionManager.refreshSession(sessionId)).thenReturn(Optional.empty());

    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setSessionRefresh(io.boomerang.proto.SessionRefreshRequest.newBuilder().build())
            .build();

    channel.writeInbound(envelope);

    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getSessionRefreshResponse().getStatus()).isEqualTo(Status.UNAUTHORIZED);
  }

  @Test
  void testHandleClientRegistrationSuccess() {
    String sessionId = "admin-session";
    String adminId = "admin-client";
    when(sessionManager.getClientId(sessionId)).thenReturn(adminId);
    when(authService.isAdmin(adminId)).thenReturn(true);

    io.boomerang.proto.ClientRegistrationRequest regRequest =
        io.boomerang.proto.ClientRegistrationRequest.newBuilder()
            .setClientId("new-client")
            .setPassword("pass")
            .setIsAdmin(false)
            .build();
    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setClientRegistration(regRequest)
            .build();

    channel.writeInbound(envelope);

    verify(authService)
        .registerClient(eq("new-client"), eq("pass"), eq(false), any(), any(), any());
    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getClientRegistrationResponse().getStatus()).isEqualTo(Status.OK);
  }

  @Test
  void testHandleClientRegistrationUnauthorized() {
    String sessionId = "user-session";
    String userId = "user-client";
    when(sessionManager.getClientId(sessionId)).thenReturn(userId);
    when(authService.isAdmin(userId)).thenReturn(false);

    io.boomerang.proto.ClientRegistrationRequest regRequest =
        io.boomerang.proto.ClientRegistrationRequest.newBuilder().setClientId("new-client").build();
    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setClientRegistration(regRequest)
            .build();

    channel.writeInbound(envelope);

    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getClientRegistrationResponse().getStatus()).isEqualTo(Status.UNAUTHORIZED);
  }

  @Test
  void testHandleClientDeregistrationSuccess() {
    String sessionId = "admin-session";
    String adminId = "admin-client";
    when(sessionManager.getClientId(sessionId)).thenReturn(adminId);
    when(authService.isAdmin(adminId)).thenReturn(true);

    io.boomerang.proto.ClientDeregistrationRequest deregRequest =
        io.boomerang.proto.ClientDeregistrationRequest.newBuilder()
            .setClientId("target-client")
            .build();
    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setClientDeregistration(deregRequest)
            .build();

    channel.writeInbound(envelope);

    verify(authService).deregisterClient("target-client");
    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getClientDeregistrationResponse().getStatus()).isEqualTo(Status.OK);
  }

  @Test
  void testHandleClientDeregistrationUnauthorized() {
    String sessionId = "user-session";
    String userId = "user-client";
    when(sessionManager.getClientId(sessionId)).thenReturn(userId);
    when(authService.isAdmin(userId)).thenReturn(false);

    io.boomerang.proto.ClientDeregistrationRequest deregRequest =
        io.boomerang.proto.ClientDeregistrationRequest.newBuilder()
            .setClientId("target-client")
            .build();
    BoomerangEnvelope envelope =
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setClientDeregistration(deregRequest)
            .build();

    channel.writeInbound(envelope);

    BoomerangEnvelope response = channel.readOutbound();
    assertThat(response.getClientDeregistrationResponse().getStatus())
        .isEqualTo(Status.UNAUTHORIZED);
  }
}
