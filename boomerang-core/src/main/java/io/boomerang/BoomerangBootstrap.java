package io.boomerang;

import io.boomerang.auth.AuthService;
import io.boomerang.config.ServerConfig;
import io.boomerang.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoomerangBootstrap {
  private static final Logger log = LoggerFactory.getLogger(BoomerangBootstrap.class);
  private final AuthService authService;
  private final SessionManager sessionManager;
  private final ServerConfig serverConfig;

  public BoomerangBootstrap(ServerConfig serverConfig) {
    this.serverConfig = serverConfig;
    this.authService = new AuthService();
    this.sessionManager = new SessionManager(serverConfig);
  }

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

  public AuthService getAuthService() {
    return authService;
  }

  public SessionManager getSessionManager() {
    return sessionManager;
  }
}
