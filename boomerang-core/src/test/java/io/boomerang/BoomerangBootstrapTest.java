package io.boomerang;

import static org.assertj.core.api.Assertions.assertThat;

import io.boomerang.config.ServerConfig;
import io.boomerang.model.Session;
import io.boomerang.proto.CallbackConfig;
import io.boomerang.proto.DLQPolicy;
import io.boomerang.proto.RetryPolicy;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoomerangBootstrapTest {
  private BoomerangBootstrap bootstrap;
  private ServerConfig serverConfig;

  @BeforeEach
  void setUp() {
    serverConfig = new ServerConfig(null); // Defaults
    bootstrap = new BoomerangBootstrap(serverConfig);
    bootstrap.start();
  }

  @Test
  void shouldHandshakeWithAdminClient() {
    String adminId = "admin";
    String adminPassword = "admin123";

    boolean authenticated = bootstrap.getAuthService().authenticate(adminId, adminPassword);
    assertThat(authenticated).isTrue();

    // Handshake flow
    CallbackConfig callbackConfig = CallbackConfig.newBuilder().build();
    RetryPolicy retryPolicy = RetryPolicy.newBuilder().build();
    DLQPolicy dlqPolicy = DLQPolicy.newBuilder().build();

    Session session =
        bootstrap
            .getSessionManager()
            .createSession(adminId, callbackConfig, retryPolicy, dlqPolicy);

    assertThat(session).isNotNull();
    assertThat(session.clientId()).isEqualTo(adminId);

    Optional<Session> retrievedSession =
        bootstrap.getSessionManager().getSession(session.sessionId());
    assertThat(retrievedSession).isPresent();
  }
}
