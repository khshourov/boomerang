package io.boomerang.auth;

import io.boomerang.config.ServerConfig;
import io.boomerang.model.CallbackConfig;
import io.boomerang.model.Client;
import io.boomerang.model.DLQPolicy;
import io.boomerang.model.RetryPolicy;
import io.boomerang.timer.StorageException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RocksDB-backed implementation of {@link ClientStore} with at-rest encryption.
 *
 * <p>Each client record is serialized manually and then encrypted before being stored in RocksDB.
 *
 * @since 1.0.0
 */
public class RocksDBClientStore implements ClientStore, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(RocksDBClientStore.class);

  private final RocksDB db;
  private final EncryptionService encryptionService;
  private final DBOptions dbOptions;
  private final ColumnFamilyOptions cfOptions;
  private final ColumnFamilyHandle defaultHandle;

  static {
    RocksDB.loadLibrary();
  }

  /**
   * Constructs a new RocksDB store for clients.
   *
   * @param serverConfig the configuration for path and master key; must be non-null
   * @throws StorageException if RocksDB fails to initialize or the master key is missing
   */
  public RocksDBClientStore(ServerConfig serverConfig) {
    String masterKey = serverConfig.getEncryptionMasterKey();
    if (masterKey == null) {
      throw new StorageException("Encryption master key (BOOMERANG_MASTER_KEY) is not set", null);
    }
    this.encryptionService = new EncryptionService(masterKey);

    String dbPath = serverConfig.getRocksDbClientPath();
    try {
      Files.createDirectories(Paths.get(dbPath));

      this.dbOptions =
          new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
      this.cfOptions = new ColumnFamilyOptions();

      List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
      cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));

      List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
      this.db = RocksDB.open(dbOptions, dbPath, cfDescriptors, cfHandles);
      this.defaultHandle = cfHandles.getFirst();

      log.info("Initialized RocksDB client store at {}", dbPath);
    } catch (IOException | RocksDBException e) {
      log.error("Failed to initialize RocksDB at {}", dbPath, e);
      throw new StorageException("Could not initialize RocksDB client store at " + dbPath, e);
    }
  }

  @Override
  public void save(Client client) {
    try {
      byte[] serialized = serialize(client);
      byte[] encrypted = encryptionService.encrypt(serialized);
      db.put(defaultHandle, client.clientId().getBytes(), encrypted);
    } catch (RocksDBException | IOException e) {
      log.error("Failed to save client {} to RocksDB", client.clientId(), e);
      throw new StorageException(
          "Persistence error during client save for " + client.clientId(), e);
    }
  }

  @Override
  public Optional<Client> findById(String clientId) {
    try {
      byte[] encrypted = db.get(defaultHandle, clientId.getBytes());
      if (encrypted == null) {
        return Optional.empty();
      }
      byte[] decrypted = encryptionService.decrypt(encrypted);
      return Optional.of(deserialize(decrypted));
    } catch (RocksDBException | IOException e) {
      log.error("Failed to retrieve client {} from RocksDB", clientId, e);
      throw new StorageException("Persistence error during client lookup for " + clientId, e);
    }
  }

  @Override
  public void delete(String clientId) {
    try {
      db.delete(defaultHandle, clientId.getBytes());
    } catch (RocksDBException e) {
      log.error("Failed to delete client {} from RocksDB", clientId, e);
      throw new StorageException("Persistence error during client deletion for " + clientId, e);
    }
  }

  @Override
  public void close() {
    defaultHandle.close();
    db.close();
    dbOptions.close();
    cfOptions.close();
  }

  private byte[] serialize(Client client) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos)) {
      dos.writeUTF(client.clientId());
      dos.writeUTF(client.hashedPassword());
      dos.writeBoolean(client.isAdmin());

      // Serialize CallbackConfig
      CallbackConfig callback = client.callbackConfig();
      if (callback == null) {
        dos.writeBoolean(false);
      } else {
        dos.writeBoolean(true);
        dos.writeInt(callback.protocol().ordinal());
        dos.writeUTF(callback.endpoint());
      }

      // Serialize RetryPolicy
      RetryPolicy retry = client.retryPolicy();
      if (retry == null) {
        dos.writeBoolean(false);
      } else {
        dos.writeBoolean(true);
        dos.writeInt(retry.maxAttempts());
        dos.writeInt(retry.strategy().ordinal());
        dos.writeLong(retry.intervalMs());
        dos.writeLong(retry.maxIntervalMs());
      }

      // Serialize DLQPolicy
      DLQPolicy dlq = client.dlqPolicy();
      if (dlq == null) {
        dos.writeBoolean(false);
      } else {
        dos.writeBoolean(true);
        dos.writeUTF(dlq.destination());
      }

      return baos.toByteArray();
    }
  }

  private Client deserialize(byte[] data) throws IOException {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais)) {
      String clientId = dis.readUTF();
      String hashedPassword = dis.readUTF();
      boolean isAdmin = dis.readBoolean();

      // Deserialize CallbackConfig
      CallbackConfig callback = null;
      if (dis.readBoolean()) {
        callback =
            new CallbackConfig(CallbackConfig.Protocol.values()[dis.readInt()], dis.readUTF());
      }

      // Deserialize RetryPolicy
      RetryPolicy retry = null;
      if (dis.readBoolean()) {
        retry =
            new RetryPolicy(
                dis.readInt(),
                RetryPolicy.BackoffStrategy.values()[dis.readInt()],
                dis.readLong(),
                dis.readLong());
      }

      // Deserialize DLQPolicy
      DLQPolicy dlq = null;
      if (dis.readBoolean()) {
        dlq = new DLQPolicy(dis.readUTF());
      }

      return new Client(clientId, hashedPassword, isAdmin, callback, retry, dlq);
    }
  }
}
