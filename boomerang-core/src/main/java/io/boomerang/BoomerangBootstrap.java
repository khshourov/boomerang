package io.boomerang;

import io.boomerang.auth.AuthService;
import io.boomerang.config.ServerConfig;
import io.boomerang.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps the Boomerang server by initializing core services and configurations.
 *
 * <p>This class coordinates the startup sequence, including registering the administrative client
 * and setting up auth and session services.
 *
 * @since 1.0.0
 */
public class BoomerangBootstrap {
  private static final Logger log = LoggerFactory.getLogger(BoomerangBootstrap.class);
  private final AuthService authService;
  private final SessionManager sessionManager;
  private final ServerConfig serverConfig;

  /**
   * Constructs a bootstrap instance with the provided server configuration.
   *
   * @param serverConfig the server configuration to use; must be non-null
   */
  public BoomerangBootstrap(ServerConfig serverConfig) {
    this.serverConfig = serverConfig;
    this.authService = new AuthService();
    this.sessionManager = new SessionManager(serverConfig);
  }

  /** Starts the Boomerang core services. */
  public void start() {
    log.info("Starting Boomerang core...");
    registerAdminClient();
  }

  private void registerAdminClient() {
    String adminId = serverConfig.getAdminClientId();
    String adminPassword = serverConfig.getAdminPassword();
    authService.registerClient(adminId, adminPassword, true);
    log.info("Admin client '{}' registered.", adminId);
  }

  /**
   * Returns the service responsible for client authentication and registration.
   *
   * @return the {@link AuthService}
   */
  public AuthService getAuthService() {
    return authService;
  }

  /**
   * Returns the manager responsible for client sessions.
   *
   * @return the {@link SessionManager}
   */
  public SessionManager getSessionManager() {
    return sessionManager;
  }
}
