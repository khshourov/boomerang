package io.boomerang.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.boomerang.model.Session;
import io.boomerang.proto.CallbackConfig;
import io.boomerang.proto.DLQPolicy;
import io.boomerang.proto.RetryPolicy;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionManagerTest {
  private SessionManager sessionManager;

  @BeforeEach
  void setUp() {
    sessionManager = new SessionManager(new io.boomerang.config.ServerConfig(null));
  }

  @Test
  void shouldCreateAndRetrieveSession() {
    CallbackConfig callbackConfig = CallbackConfig.newBuilder()
        .setProtocol(CallbackConfig.Protocol.HTTP)
        .setEndpoint("http://localhost:8080/callback")
        .build();
    RetryPolicy retryPolicy = RetryPolicy.newBuilder()
        .setMaxAttempts(3)
        .setStrategy(RetryPolicy.BackoffStrategy.FIXED)
        .setIntervalMs(1000)
        .build();
    DLQPolicy dlqPolicy = DLQPolicy.newBuilder()
        .setDestination("my-dlq")
        .build();

    Session session = sessionManager.createSession(
        "client-1", callbackConfig, retryPolicy, dlqPolicy);

    assertThat(session).isNotNull();
    assertThat(session.sessionId()).isNotNull();
    assertThat(session.clientId()).isEqualTo("client-1");
    assertThat(session.callbackConfig().getEndpoint()).isEqualTo("http://localhost:8080/callback");

    Optional<Session> retrievedSession = sessionManager.getSession(session.sessionId());
    assertThat(retrievedSession).isPresent();
    assertThat(retrievedSession.get().sessionId()).isEqualTo(session.sessionId());
  }

  @Test
  void shouldRefreshSession() {
    CallbackConfig callbackConfig = CallbackConfig.newBuilder().build();
    RetryPolicy retryPolicy = RetryPolicy.newBuilder().build();
    DLQPolicy dlqPolicy = DLQPolicy.newBuilder().build();

    Session session = sessionManager.createSession(
        "client-1", callbackConfig, retryPolicy, dlqPolicy);
    String originalSessionId = session.sessionId();

    Optional<Session> refreshedSession = sessionManager.refreshSession(originalSessionId);

    assertThat(refreshedSession).isPresent();
    assertThat(refreshedSession.get().sessionId()).isEqualTo(originalSessionId);
    assertThat(refreshedSession.get().expiresAt()).isAfter(session.expiresAt());
  }

  @Test
  void shouldNotRetrieveInvalidSession() {
    Optional<Session> retrievedSession = sessionManager.getSession("invalid-session");
    assertThat(retrievedSession).isEmpty();
  }

  @Test
  void shouldInvalidateSession() {
    CallbackConfig callbackConfig = CallbackConfig.newBuilder().build();
    RetryPolicy retryPolicy = RetryPolicy.newBuilder().build();
    DLQPolicy dlqPolicy = DLQPolicy.newBuilder().build();

    Session session = sessionManager.createSession(
        "client-1", callbackConfig, retryPolicy, dlqPolicy);
    sessionManager.invalidateSession(session.sessionId());

    Optional<Session> retrievedSession = sessionManager.getSession(session.sessionId());
    assertThat(retrievedSession).isEmpty();
  }
}
