package io.boomerang.auth;

import io.boomerang.config.ServerConfig;
import io.boomerang.model.CallbackConfig;
import io.boomerang.model.Client;
import io.boomerang.model.DLQPolicy;
import io.boomerang.model.RetryPolicy;
import io.boomerang.model.Session;
import io.boomerang.session.SessionManager;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for client registration and authentication.
 *
 * <p>This service manages client credentials using PBKDF2 hashing with a unique salt for each
 * client. It also manages default execution policies for each client.
 *
 * @since 1.0.0
 */
public class AuthService {
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private final ClientStore clientStore;
  private final ServerConfig serverConfig;
  private final SessionManager sessionManager;
  private static final int ITERATIONS = 10000;
  private static final int KEY_LENGTH = 256;
  private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

  /**
   * Constructs an authentication service.
   *
   * @param clientStore the persistent store for clients; must be non-null
   * @param serverConfig the configuration for admin provisioning; must be non-null
   * @param sessionManager the manager for client sessions; must be non-null
   */
  public AuthService(
      ClientStore clientStore, ServerConfig serverConfig, SessionManager sessionManager) {
    this.clientStore = clientStore;
    this.serverConfig = serverConfig;
    this.sessionManager = sessionManager;
    provisionAdmin();
  }

  private void provisionAdmin() {
    String adminId = serverConfig.getAdminClientId();
    if (clientStore.findById(adminId).isEmpty()) {
      log.info("Provisioning initial admin client: {}", adminId);
      registerClient(adminId, serverConfig.getAdminPassword(), true, null, null, null);
    }
  }

  /**
   * Registers a new client with the specified credentials and policies.
   *
   * @param clientId the unique identifier for the client; must be non-null
   * @param password the plain-text password to be hashed; must be non-null
   * @param isAdmin whether the client should be granted administrative privileges
   * @param callback default callback config; can be null
   * @param retry default retry policy; can be null
   * @param dlq default dead-letter queue policy; can be null
   */
  public void registerClient(
      String clientId,
      String password,
      boolean isAdmin,
      CallbackConfig callback,
      RetryPolicy retry,
      DLQPolicy dlq) {
    String hashedPassword = hashPassword(password);
    clientStore.save(new Client(clientId, hashedPassword, isAdmin, callback, retry, dlq));
    log.info("Registered client: {} (Admin: {})", clientId, isAdmin);
  }

  /**
   * Registers a new client if the requesting client has administrative privileges.
   *
   * @param adminClientId the ID of the client performing the registration; must be non-null
   * @param newClientId the unique identifier for the new client; must be non-null
   * @param newPassword the plain-text password for the new client; must be non-null
   * @param isNewAdmin whether the new client should be granted administrative privileges
   * @param callback default callback config; can be null
   * @param retry default retry policy; can be null
   * @param dlq default dead-letter queue policy; can be null
   * @return {@code true} if the registration was successful, {@code false} if the admin client is
   *     not authorized
   */
  public boolean registerClientByAdmin(
      String adminClientId,
      String newClientId,
      String newPassword,
      boolean isNewAdmin,
      CallbackConfig callback,
      RetryPolicy retry,
      DLQPolicy dlq) {
    Optional<Client> admin = getClient(adminClientId);
    if (admin.isEmpty() || !admin.get().isAdmin()) {
      log.warn("Unauthorized registration attempt of '{}' by '{}'", newClientId, adminClientId);
      return false;
    }

    registerClient(newClientId, newPassword, isNewAdmin, callback, retry, dlq);
    return true;
  }

  /**
   * Deregisters a client if the requesting client has administrative privileges.
   *
   * @param adminClientId the ID of the client performing the deregistration; must be non-null
   * @param targetClientId the unique identifier for the client to remove; must be non-null
   * @return {@code true} if the deregistration was successful, {@code false} if the admin client is
   *     not authorized
   */
  public boolean deregisterClientByAdmin(String adminClientId, String targetClientId) {
    Optional<Client> admin = getClient(adminClientId);
    if (admin.isEmpty() || !admin.get().isAdmin()) {
      log.warn(
          "Unauthorized deregistration attempt of '{}' by '{}'", targetClientId, adminClientId);
      return false;
    }

    clientStore.delete(targetClientId);
    log.info("Client {} deregistered by admin {}", targetClientId, adminClientId);
    return true;
  }

  /**
   * Authenticates a client and initiates a new session.
   *
   * <p>Policies are resolved from the client's registered defaults.
   *
   * @param clientId the ID of the client to authenticate
   * @param password the plain-text password to verify
   * @return an {@link Optional} containing the new {@link Session} if successful, or empty
   *     otherwise
   */
  public Optional<Session> authenticate(String clientId, String password) {
    try {
      Optional<Client> clientOpt = clientStore.findById(clientId);

      if (clientOpt.isEmpty() || !verifyPassword(password, clientOpt.get().hashedPassword())) {
        log.warn("Failed authentication attempt for client: {}", clientId);
        return Optional.empty();
      }

      Client client = clientOpt.get();

      Session session =
          sessionManager.createSession(
              clientId, client.callbackConfig(), client.retryPolicy(), client.dlqPolicy());

      log.info("Client {} authenticated successfully. Session: {}", clientId, session.sessionId());
      return Optional.of(session);
    } catch (Exception e) {
      log.error("Error during authentication for client: {}", clientId, e);
      return Optional.empty();
    }
  }

  /**
   * Checks if the given client has administrative privileges.
   *
   * @param clientId the identifier of the client to check; must be non-null
   * @return {@code true} if the client is an admin, {@code false} otherwise
   */
  public boolean isAdmin(String clientId) {
    return getClient(clientId).map(Client::isAdmin).orElse(false);
  }

  /**
   * Deregisters a client.
   *
   * @param targetClientId the unique identifier for the client to remove; must be non-null
   */
  public void deregisterClient(String targetClientId) {
    clientStore.delete(targetClientId);
    log.info("Client {} deregistered", targetClientId);
  }

  /**
   * Retrieves a client by their identifier.
   *
   * @param clientId the unique identifier for the client; must be non-null
   * @return an {@link Optional} containing the {@link Client} if found, or empty if not
   */
  public Optional<Client> getClient(String clientId) {
    return clientStore.findById(clientId);
  }

  private String hashPassword(String password) {
    byte[] salt = new byte[16];
    SECURE_RANDOM.nextBytes(salt);
    byte[] hash = pbkdf2(password.toCharArray(), salt);
    return Base64.getEncoder().encodeToString(salt)
        + ":"
        + Base64.getEncoder().encodeToString(hash);
  }

  private boolean verifyPassword(String password, String storedHash) {
    String[] parts = storedHash.split(":");
    if (parts.length != 2) {
      return false;
    }
    byte[] salt = Base64.getDecoder().decode(parts[0]);
    byte[] hash = Base64.getDecoder().decode(parts[1]);
    byte[] testHash = pbkdf2(password.toCharArray(), salt);
    return java.util.Arrays.equals(hash, testHash);
  }

  private byte[] pbkdf2(char[] password, byte[] salt) {
    PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
    try {
      SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
      return factory.generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new SecurityException("Error hashing password", e);
    }
  }
}
