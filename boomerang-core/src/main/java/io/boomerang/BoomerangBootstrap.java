package io.boomerang;

import io.boomerang.auth.AuthService;
import io.boomerang.auth.ClientStore;
import io.boomerang.auth.RocksDBClientStore;
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
  private final ClientStore clientStore;

  /**
   * Constructs a bootstrap instance with the provided server configuration.
   *
   * @param serverConfig the server configuration to use; must be non-null
   */
  public BoomerangBootstrap(ServerConfig serverConfig) {
    this.serverConfig = serverConfig;
    this.clientStore = new RocksDBClientStore(serverConfig);
    this.authService = new AuthService(clientStore, serverConfig);
    this.sessionManager = new SessionManager(serverConfig);
  }

  /** Starts the Boomerang core services. */
  public void start() {
    log.info("Starting Boomerang core...");
  }

  /**
   * Closes the Boomerang core services and releases resources.
   *
   * @throws Exception if an error occurs during shutdown
   */
  public void close() throws Exception {
    log.info("Stopping Boomerang core...");
    if (clientStore != null) {
      clientStore.close();
    }
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
