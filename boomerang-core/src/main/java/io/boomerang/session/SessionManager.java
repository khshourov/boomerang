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

/**
 * Manager responsible for the lifecycle of client sessions.
 *
 * <p>This manager creates, retrieves, refreshes, and invalidates sessions for authenticated
 * clients. It also handles session expiration and cleanup.
 *
 * @since 1.0.0
 */
public class SessionManager {
  private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final Duration sessionDuration;

  /**
   * Constructs a session manager with configuration-based session duration.
   *
   * @param serverConfig the server configuration providing session timeout; must be non-null
   */
  public SessionManager(io.boomerang.config.ServerConfig serverConfig) {
    this.sessionDuration = Duration.ofMinutes(serverConfig.getSessionTimeoutMinutes());
  }

  /**
   * Creates a new session for a client with specified policies and configurations.
   *
   * @param clientId the ID of the client for whom to create a session; must be non-null
   * @param callbackConfig the configuration for callbacks; can be {@code null}
   * @param retryPolicy the policy for retries; can be {@code null}
   * @param dlqPolicy the policy for dead-letter queues; can be {@code null}
   * @return a new {@link Session} object
   */
  public Session createSession(
      String clientId,
      CallbackConfig callbackConfig,
      RetryPolicy retryPolicy,
      DLQPolicy dlqPolicy) {
    String sessionId = UUID.randomUUID().toString();
    Instant expiresAt = Instant.now().plus(sessionDuration);
    Session session =
        new Session(sessionId, clientId, callbackConfig, retryPolicy, dlqPolicy, expiresAt);
    sessions.put(sessionId, session);
    log.info("Created session {} for client {}", sessionId, clientId);
    return session;
  }

  /**
   * Retrieves an active, non-expired session by its identifier.
   *
   * @param sessionId the identifier of the session to retrieve; must be non-null
   * @return an {@link Optional} containing the {@link Session} if found and active, or empty
   *     otherwise
   */
  public Optional<Session> getSession(String sessionId) {
    Session session = sessions.get(sessionId);
    if (session == null || session.isExpired()) {
      return Optional.empty();
    }
    return Optional.of(session);
  }

  /**
   * Refreshes the expiration time of an existing session.
   *
   * @param sessionId the identifier of the session to refresh; must be non-null
   * @return an {@link Optional} containing the refreshed {@link Session}, or empty if not found or
   *     already expired
   */
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

  /**
   * Explicitly invalidates and removes a session.
   *
   * @param sessionId the identifier of the session to invalidate; must be non-null
   */
  public void invalidateSession(String sessionId) {
    sessions.remove(sessionId);
  }

  /** Periodically cleans up expired sessions from the manager's memory. */
  public void cleanupExpiredSessions() {
    sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
  }
}
