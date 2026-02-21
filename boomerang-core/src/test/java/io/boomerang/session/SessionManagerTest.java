package io.boomerang.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.boomerang.model.CallbackConfig;
import io.boomerang.model.DLQPolicy;
import io.boomerang.model.RetryPolicy;
import io.boomerang.model.Session;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
  void shouldVerifySessionExpiration() {
    Session activeSession =
        new Session("s1", "c1", null, null, null, Instant.now().plus(1, ChronoUnit.HOURS));
    assertThat(activeSession.isExpired()).isFalse();

    Session expiredSession =
        new Session("s2", "c2", null, null, null, Instant.now().minus(1, ChronoUnit.HOURS));
    assertThat(expiredSession.isExpired()).isTrue();
  }

  @Test
  void shouldCreateAndRetrieveSession() {
    CallbackConfig callbackConfig =
        new CallbackConfig(CallbackConfig.Protocol.HTTP, "http://localhost:8080/callback");
    RetryPolicy retryPolicy = new RetryPolicy(3, RetryPolicy.BackoffStrategy.FIXED, 1000, 0);
    DLQPolicy dlqPolicy = new DLQPolicy("my-dlq");

    Session session =
        sessionManager.createSession("client-1", callbackConfig, retryPolicy, dlqPolicy);

    assertThat(session).isNotNull();
    assertThat(session.sessionId()).isNotNull();
    assertThat(session.clientId()).isEqualTo("client-1");
    assertThat(session.callbackConfig().endpoint()).isEqualTo("http://localhost:8080/callback");

    Optional<Session> retrievedSession = sessionManager.getSession(session.sessionId());
    assertThat(retrievedSession).isPresent();
    assertThat(retrievedSession.get().sessionId()).isEqualTo(session.sessionId());
  }

  @Test
  void shouldRefreshSession() {
    CallbackConfig callbackConfig = new CallbackConfig(CallbackConfig.Protocol.TCP, "");
    RetryPolicy retryPolicy = new RetryPolicy(0, RetryPolicy.BackoffStrategy.FIXED, 0, 0);
    DLQPolicy dlqPolicy = new DLQPolicy("");

    Session session =
        sessionManager.createSession("client-1", callbackConfig, retryPolicy, dlqPolicy);
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
    CallbackConfig callbackConfig = new CallbackConfig(CallbackConfig.Protocol.TCP, "");
    RetryPolicy retryPolicy = new RetryPolicy(0, RetryPolicy.BackoffStrategy.FIXED, 0, 0);
    DLQPolicy dlqPolicy = new DLQPolicy("");

    Session session =
        sessionManager.createSession("client-1", callbackConfig, retryPolicy, dlqPolicy);
    sessionManager.invalidateSession(session.sessionId());

    Optional<Session> retrievedSession = sessionManager.getSession(session.sessionId());
    assertThat(retrievedSession).isEmpty();
  }

  @Test
  void shouldValidateSession() {
    Session session = sessionManager.createSession("client-1", null, null, null);

    assertThat(sessionManager.isValid(session.sessionId())).isTrue();
    assertThat(sessionManager.isValid("non-existent")).isFalse();
    assertThat(sessionManager.isValid(null)).isFalse();
  }

  @Test
  void shouldGetClientId() {
    Session session = sessionManager.createSession("client-1", null, null, null);

    assertThat(sessionManager.getClientId(session.sessionId())).isEqualTo("client-1");
    assertThat(sessionManager.getClientId("non-existent")).isNull();
  }
}
