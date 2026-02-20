package io.boomerang.timer;

import io.boomerang.config.ServerConfig;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RocksDB-backed implementation of {@link DLQStore}.
 *
 * <p>This store maintains a single Column Family:
 *
 * <ul>
 *   <li>{@code dlq}: Key: [clientId]:[taskId]. Value: Serialized {@link DLQEntry} (task + error).
 * </ul>
 *
 * @since 1.0.0
 */
public class RocksDBDLQStore implements DLQStore, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(RocksDBDLQStore.class);

  private static final String CF_DLQ = "dlq";
  private static final String KEY_SEPARATOR = ":";

  private final RocksDB db;
  private final DBOptions dbOptions;
  private final ColumnFamilyOptions cfOptions;
  private final ColumnFamilyHandle defaultHandle;
  private final ColumnFamilyHandle dlqHandle;

  static {
    RocksDB.loadLibrary();
  }

  public RocksDBDLQStore(ServerConfig serverConfig) {
    String dbPath = serverConfig.getRocksDbDlqPath();
    try {
      Files.createDirectories(Paths.get(dbPath));

      this.dbOptions =
          new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
      this.cfOptions = new ColumnFamilyOptions();

      List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
      cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));
      cfDescriptors.add(new ColumnFamilyDescriptor(CF_DLQ.getBytes(), cfOptions));

      List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
      this.db = RocksDB.open(dbOptions, dbPath, cfDescriptors, cfHandles);
      this.defaultHandle = cfHandles.get(0);
      this.dlqHandle = cfHandles.get(1);

      log.info("Initialized RocksDB DLQ store at {}", dbPath);
    } catch (IOException | RocksDBException e) {
      log.error("Failed to initialize RocksDB DLQ at {}", dbPath, e);
      throw new StorageException("Could not initialize RocksDB DLQ store at " + dbPath, e);
    }
  }

  @Override
  public void save(DLQEntry entry) {
    byte[] key = createKey(entry.task().getClientId(), entry.task().getTaskId());
    try (WriteOptions writeOptions = new WriteOptions()) {
      byte[] serializedEntry = DLQEntrySerializer.serialize(entry);
      db.put(dlqHandle, key, serializedEntry);
    } catch (RocksDBException | IOException e) {
      log.error("Failed to save task {} to DLQ", entry.task().getTaskId(), e);
      throw new StorageException(
          "Persistence error during DLQ save for " + entry.task().getTaskId(), e);
    }
  }

  @Override
  public Collection<DLQEntry> findAll() {
    List<DLQEntry> entries = new ArrayList<>();
    try (RocksIterator iter = db.newIterator(dlqHandle)) {
      for (iter.seekToFirst(); iter.isValid(); iter.next()) {
        try {
          entries.add(DLQEntrySerializer.deserialize(iter.value()));
        } catch (IOException e) {
          log.warn("Failed to deserialize DLQ entry, skipping", e);
        }
      }
    }
    return entries;
  }

  @Override
  public Collection<DLQEntry> findAll(String clientId) {
    List<DLQEntry> entries = new ArrayList<>();
    byte[] prefix = (clientId + KEY_SEPARATOR).getBytes();
    try (RocksIterator iter = db.newIterator(dlqHandle)) {
      for (iter.seek(prefix); iter.isValid(); iter.next()) {
        byte[] key = iter.key();
        if (!startsWith(key, prefix)) {
          break;
        }
        try {
          entries.add(DLQEntrySerializer.deserialize(iter.value()));
        } catch (IOException e) {
          log.warn("Failed to deserialize DLQ entry for client {}, skipping", clientId, e);
        }
      }
    }
    return entries;
  }

  @Override
  public Optional<DLQEntry> findEntryById(String clientId, String taskId) {
    byte[] key = createKey(clientId, taskId);
    try {
      byte[] data = db.get(dlqHandle, key);
      if (data == null) {
        return Optional.empty();
      }
      return Optional.of(DLQEntrySerializer.deserialize(data));
    } catch (RocksDBException | IOException e) {
      log.error("Failed to find entry for task {} in DLQ", taskId, e);
      throw new StorageException("Persistence error during DLQ lookup for " + taskId, e);
    }
  }

  @Override
  public void delete(String clientId, String taskId) {
    byte[] key = createKey(clientId, taskId);
    try (WriteOptions writeOptions = new WriteOptions()) {
      db.delete(dlqHandle, key);
    } catch (RocksDBException e) {
      log.error("Failed to delete task {} from DLQ", taskId, e);
      throw new StorageException("Persistence error during DLQ deletion for " + taskId, e);
    }
  }

  @Override
  public void clear() {
    try (RocksIterator iter = db.newIterator(dlqHandle)) {
      for (iter.seekToFirst(); iter.isValid(); iter.next()) {
        try (WriteOptions writeOptions = new WriteOptions()) {
          db.delete(dlqHandle, iter.key());
        } catch (RocksDBException e) {
          log.error("Failed to delete entry during clear", e);
        }
      }
    }
  }

  @Override
  public void close() {
    dlqHandle.close();
    defaultHandle.close();
    db.close();
    dbOptions.close();
    cfOptions.close();
  }

  private byte[] createKey(String clientId, String taskId) {
    return (clientId + KEY_SEPARATOR + taskId).getBytes();
  }

  private boolean startsWith(byte[] array, byte[] prefix) {
    if (array.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (array[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static class DLQEntrySerializer {
    static byte[] serialize(DLQEntry entry) throws IOException {
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
          DataOutputStream dos = new DataOutputStream(baos)) {
        byte[] taskData = TimerTaskSerializer.serialize(entry.task());
        dos.writeInt(taskData.length);
        dos.write(taskData);

        if (entry.errorMessage() == null) {
          dos.writeBoolean(false);
        } else {
          dos.writeBoolean(true);
          dos.writeUTF(entry.errorMessage());
        }
        return baos.toByteArray();
      }
    }

    static DLQEntry deserialize(byte[] data) throws IOException {
      try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
          DataInputStream dis = new DataInputStream(bais)) {
        int taskDataLength = dis.readInt();
        byte[] taskData = new byte[taskDataLength];
        dis.readFully(taskData);
        TimerTask task = TimerTaskSerializer.deserialize(taskData);

        String errorMessage = null;
        if (dis.readBoolean()) {
          errorMessage = dis.readUTF();
        }
        return new DLQEntry(task, errorMessage);
      }
    }
  }
}
