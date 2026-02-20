package io.boomerang.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.boomerang.config.ServerConfig;
import io.boomerang.model.Client;
import io.boomerang.proto.CallbackConfig;
import io.boomerang.proto.DLQPolicy;
import io.boomerang.proto.RetryPolicy;
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
    System.clearProperty("rocksdb.client.path");
  }

  @Test
  void shouldSaveAndFindClient() {
    Client client = new Client("client-1", "hashed-pass", true, null, null, null);
    store.save(client);

    Optional<Client> found = store.findById("client-1");
    assertThat(found).isPresent().contains(client);
  }

  @Test
  void shouldPersistPolicies() {
    CallbackConfig callback = CallbackConfig.newBuilder().setEndpoint("http://test").build();
    RetryPolicy retry = RetryPolicy.newBuilder().setMaxAttempts(3).build();
    DLQPolicy dlq = DLQPolicy.newBuilder().setDestination("dlq-1").build();

    Client client = new Client("client-1", "hashed-pass", false, callback, retry, dlq);
    store.save(client);

    Optional<Client> found = store.findById("client-1");
    assertThat(found).isPresent();
    assertThat(found.get().callbackConfig().getEndpoint()).isEqualTo("http://test");
    assertThat(found.get().retryPolicy().getMaxAttempts()).isEqualTo(3);
    assertThat(found.get().dlqPolicy().getDestination()).isEqualTo("dlq-1");
  }

  @Test
  void shouldReturnEmptyForMissingClient() {
    assertThat(store.findById("missing")).isEmpty();
  }

  @Test
  void shouldDeleteClient() {
    Client client = new Client("client-1", "hashed-pass", true, null, null, null);
    store.save(client);
    assertThat(store.findById("client-1")).isPresent();

    store.delete("client-1");
    assertThat(store.findById("client-1")).isEmpty();
  }

  @Test
  void shouldPersistBetweenInstances() {
    Client client = new Client("client-1", "hashed-pass", true, null, null, null);
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
    System.setProperty("rocksdb.client.path", path);
    return new ServerConfig(null);
  }
}
