package io.boomerang;

import static org.assertj.core.api.Assertions.assertThat;

import io.boomerang.config.ServerConfig;
import io.boomerang.model.Session;
import io.boomerang.proto.CallbackConfig;
import io.boomerang.proto.DLQPolicy;
import io.boomerang.proto.RetryPolicy;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoomerangBootstrapTest {
  @TempDir Path tempDir;
  private BoomerangBootstrap bootstrap;
  private ServerConfig serverConfig;
  private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

  @BeforeEach
  void setUp() {
    System.setProperty("BOOMERANG_MASTER_KEY", MASTER_KEY);
    System.setProperty("rocksdb.client.path", tempDir.resolve("clients").toString());
    System.setProperty("rocksdb.path", tempDir.resolve("rocksdb").toString());

    serverConfig = new ServerConfig(null); // Defaults
    bootstrap = new BoomerangBootstrap(serverConfig);
    bootstrap.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (bootstrap != null) {
      bootstrap.close();
    }
    System.clearProperty("BOOMERANG_MASTER_KEY");
    System.clearProperty("rocksdb.client.path");
    System.clearProperty("rocksdb.path");
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
