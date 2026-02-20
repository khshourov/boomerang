package io.boomerang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.boomerang.config.ServerConfig;
import io.boomerang.model.Session;
import io.boomerang.timer.TimerTask;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
    System.setProperty("rocksdb.dlq.path", tempDir.resolve("dlq").toString());
    System.setProperty("timer.imminent.window.ms", "1000"); // Fast window for test
    System.setProperty("timer.advance.clock.interval.ms", "50");

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
    System.clearProperty("rocksdb.dlq.path");
    System.clearProperty("timer.imminent.window.ms");
    System.clearProperty("timer.advance.clock.interval.ms");
  }

  @Test
  void shouldHandshakeWithAdminClient() {
    String adminId = "admin";
    String adminPassword = "admin123";

    Optional<Session> sessionOpt = bootstrap.getAuthService().authenticate(adminId, adminPassword);
    assertThat(sessionOpt).isPresent();
    Session session = sessionOpt.get();

    assertThat(session).isNotNull();
    assertThat(session.clientId()).isEqualTo(adminId);

    Optional<Session> retrievedSession =
        bootstrap.getSessionManager().getSession(session.sessionId());
    assertThat(retrievedSession).isPresent();
  }

  @Test
  void shouldRecoverTasksOnRestart() throws Exception {
    String taskId = "recover-me";
    // 1. Create a task that expires soon
    TimerTask task = new TimerTask(taskId, "admin", 500, null, 0, () -> {});
    bootstrap.getTimer().add(task);

    // 2. Shut down the bootstrap
    bootstrap.close();

    // 3. Restart bootstrap pointing to same storage
    // We reuse serverConfig but create NEW bootstrap instance
    bootstrap = new BoomerangBootstrap(serverConfig);
    bootstrap.start();

    // 4. Verify the task is eventually executed.
    // Since our placeholder dispatcher just logs, we could check if it fires.
    // To make it testable, we'll use a hack or rely on the log?
    // Actually, TieredTimer.handleExpiredTask deletes from store AFTER dispatch.
    // So if it disappears from the store, it was dispatched.

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // Should be gone from store after dispatch
              assertThat(bootstrap.getTimer().get(taskId)).isEmpty();
            });
  }
}
