package io.boomerang.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.boomerang.model.Client;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService();
  }

  @Test
  void shouldRegisterAndAuthenticateClient() {
    authService.registerClient("test-client", "password123", false);

    boolean authenticated = authService.authenticate("test-client", "password123");
    assertThat(authenticated).isTrue();

    Optional<Client> client = authService.getClient("test-client");
    assertThat(client).isPresent();
    assertThat(client.get().isAdmin()).isFalse();
  }

  @Test
  void shouldFailAuthenticationWithWrongPassword() {
    authService.registerClient("test-client", "password123", false);

    boolean authenticated = authService.authenticate("test-client", "wrong-password");
    assertThat(authenticated).isFalse();
  }

  @Test
  void shouldFailAuthenticationForUnknownClient() {
    boolean authenticated = authService.authenticate("unknown-client", "password");
    assertThat(authenticated).isFalse();
  }

  @Test
  void shouldRegisterNewClientByAdmin() {
    authService.registerClient("admin", "admin123", true);

    boolean success = authService.registerClientByAdmin("admin", "new-client", "pass123", false);
    assertThat(success).isTrue();

    assertThat(authService.authenticate("new-client", "pass123")).isTrue();
  }

  @Test
  void shouldFailToRegisterNewClientByNonAdmin() {
    authService.registerClient("user", "user123", false);

    boolean success = authService.registerClientByAdmin("user", "new-client", "pass123", false);
    assertThat(success).isFalse();

    assertThat(authService.getClient("new-client")).isEmpty();
  }
}
