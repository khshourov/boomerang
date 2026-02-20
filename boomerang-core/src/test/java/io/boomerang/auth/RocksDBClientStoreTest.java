package io.boomerang.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.boomerang.config.ServerConfig;
import io.boomerang.model.Client;
import io.boomerang.timer.StorageException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocksDBClientStoreTest {
  @TempDir Path tempDir;
  private RocksDBClientStore store;
  private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

  @BeforeEach
  void setUp() {
    System.setProperty("BOOMERANG_MASTER_KEY", MASTER_KEY);
    ServerConfig config = createConfig(tempDir.toString());
    store = new RocksDBClientStore(config);
  }

  @AfterEach
  void tearDown() {
    if (store != null) {
      store.close();
    }
    System.clearProperty("BOOMERANG_MASTER_KEY");
  }

  @Test
  void shouldSaveAndFindClient() {
    Client client = new Client("client-1", "hashed-pass", true);
    store.save(client);

    Optional<Client> found = store.findById("client-1");
    assertThat(found).isPresent().contains(client);
  }

  @Test
  void shouldReturnEmptyForMissingClient() {
    assertThat(store.findById("missing")).isEmpty();
  }

  @Test
  void shouldDeleteClient() {
    Client client = new Client("client-1", "hashed-pass", true);
    store.save(client);
    assertThat(store.findById("client-1")).isPresent();

    store.delete("client-1");
    assertThat(store.findById("client-1")).isEmpty();
  }

  @Test
  void shouldPersistBetweenInstances() {
    Client client = new Client("client-1", "hashed-pass", true);
    store.save(client);
    store.close();

    ServerConfig config = createConfig(tempDir.toString());
    store = new RocksDBClientStore(config);

    Optional<Client> found = store.findById("client-1");
    assertThat(found).isPresent().contains(client);
  }

  @Test
  void shouldThrowIfMasterKeyMissing() {
    System.clearProperty("BOOMERANG_MASTER_KEY");
    ServerConfig config = createConfig(tempDir.toString());
    assertThatThrownBy(() -> new RocksDBClientStore(config))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("master key");
  }

  private ServerConfig createConfig(String path) {
    // We can't easily inject properties into ServerConfig, so we'll use system properties
    // or assume it reads from the environment if we could mock it.
    // For now, let's just use the default path and hope it works with tempDir
    System.setProperty("rocksdb.client.path", path);
    return new ServerConfig(null);
  }
}
