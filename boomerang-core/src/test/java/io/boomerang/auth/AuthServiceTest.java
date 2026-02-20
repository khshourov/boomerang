package io.boomerang.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.boomerang.config.ServerConfig;
import io.boomerang.model.CallbackConfig;
import io.boomerang.model.Client;
import io.boomerang.model.Session;
import io.boomerang.session.SessionManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
  private AuthService authService;
  private ClientStore clientStore;
  private SessionManager sessionManager;

  @BeforeEach
  void setUp() {
    clientStore = new MemoryClientStore();
    ServerConfig serverConfig = new ServerConfig(null);
    sessionManager = new SessionManager(serverConfig);
    authService = new AuthService(clientStore, serverConfig, sessionManager);
  }

  @Test
  void shouldRegisterAndAuthenticateClient() {
    authService.registerClient("test-client", "password123", false, null, null, null);

    Optional<Session> session = authService.authenticate("test-client", "password123");
    assertThat(session).isPresent();

    Optional<Client> client = authService.getClient("test-client");
    assertThat(client).isPresent();
    assertThat(client.get().isAdmin()).isFalse();
  }

  @Test
  void shouldReturnSessionWithDefaultPolicies() {
    CallbackConfig defaultCallback =
        new CallbackConfig(CallbackConfig.Protocol.HTTP, "http://default");
    authService.registerClient("test-client", "password123", false, defaultCallback, null, null);

    Optional<Session> session = authService.authenticate("test-client", "password123");

    assertThat(session).isPresent();
    assertThat(session.get().clientId()).isEqualTo("test-client");
    assertThat(session.get().callbackConfig().endpoint()).isEqualTo("http://default");
  }

  @Test
  void shouldFailAuthenticationWithWrongPassword() {
    authService.registerClient("test-client", "password123", false, null, null, null);

    Optional<Session> session = authService.authenticate("test-client", "wrong-password");
    assertThat(session).isEmpty();
  }

  @Test
  void shouldFailAuthenticationForUnknownClient() {
    Optional<Session> session = authService.authenticate("unknown-client", "password");
    assertThat(session).isEmpty();
  }

  @Test
  void shouldRegisterNewClientByAdmin() {
    authService.registerClient("admin-user", "admin123", true, null, null, null);

    boolean success =
        authService.registerClientByAdmin(
            "admin-user", "new-client", "pass123", false, null, null, null);
    assertThat(success).isTrue();

    Optional<Session> session = authService.authenticate("new-client", "pass123");
    assertThat(session).isPresent();
  }

  @Test
  void shouldFailToRegisterNewClientByNonAdmin() {
    authService.registerClient("user", "user123", false, null, null, null);

    boolean success =
        authService.registerClientByAdmin("user", "new-client", "pass123", false, null, null, null);
    assertThat(success).isFalse();

    assertThat(authService.getClient("new-client")).isEmpty();
  }

  @Test
  void shouldDeregisterClientByAdmin() {
    authService.registerClient("admin", "pass", true, null, null, null);
    authService.registerClient("target", "pass", false, null, null, null);

    boolean success = authService.deregisterClientByAdmin("admin", "target");
    assertThat(success).isTrue();
    assertThat(authService.getClient("target")).isEmpty();
  }

  @Test
  void shouldFailToDeregisterByNonAdmin() {
    authService.registerClient("user", "pass", false, null, null, null);
    authService.registerClient("target", "pass", false, null, null, null);

    boolean success = authService.deregisterClientByAdmin("user", "target");
    assertThat(success).isFalse();
    assertThat(authService.getClient("target")).isPresent();
  }
}
