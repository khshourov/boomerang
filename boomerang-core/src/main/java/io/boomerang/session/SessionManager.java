package io.boomerang.session;

import io.boomerang.model.Session;
import io.boomerang.proto.CallbackConfig;
import io.boomerang.proto.DLQPolicy;
import io.boomerang.proto.RetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionManager {
  private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final Duration sessionDuration;

  public SessionManager(io.boomerang.config.ServerConfig serverConfig) {
    this.sessionDuration = Duration.ofMinutes(serverConfig.getSessionTimeoutMinutes());
  }

  public Session createSession(
      String clientId,
      CallbackConfig callbackConfig,
      RetryPolicy retryPolicy,
      DLQPolicy dlqPolicy) {
    String sessionId = UUID.randomUUID().toString();
    Instant expiresAt = Instant.now().plus(sessionDuration);
    Session session =
        new Session(
            sessionId, clientId, callbackConfig, retryPolicy, dlqPolicy, expiresAt);
    sessions.put(sessionId, session);
    log.info("Created session {} for client {}", sessionId, clientId);
    return session;
  }

  public Optional<Session> getSession(String sessionId) {
    Session session = sessions.get(sessionId);
    if (session == null || session.isExpired()) {
      return Optional.empty();
    }
    return Optional.of(session);
  }

  public Optional<Session> refreshSession(String sessionId) {
    Session session = sessions.get(sessionId);
    if (session == null || session.isExpired()) {
      return Optional.empty();
    }

    Session refreshedSession =
        new Session(
            session.sessionId(),
            session.clientId(),
            session.callbackConfig(),
            session.retryPolicy(),
            session.dlqPolicy(),
            Instant.now().plus(sessionDuration));
    sessions.put(sessionId, refreshedSession);
    return Optional.of(refreshedSession);
  }

  public void invalidateSession(String sessionId) {
    sessions.remove(sessionId);
  }

  // Cleanup should be called periodically by a background task (later).
  public void cleanupExpiredSessions() {
    sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
  }
}
