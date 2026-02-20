package io.boomerang.timer;

import io.boomerang.config.ServerConfig;
import java.io.IOException;
import java.nio.ByteBuffer;
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
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RocksDB-backed implementation of {@link LongTermTaskStore} for persistent storage.
 *
 * <p>This store maintains two Column Families:
 *
 * <ul>
 *   <li>{@code time_index}: Primary index sorted by {@code expirationMs}. Key: [expiration (8b
 *       BE)][taskId]. Value: Serialized task.
 *   <li>{@code id_index}: Secondary index for ID-based lookups. Key: [taskId]. Value: [expiration
 *       (8b BE)].
 * </ul>
 *
 * @since 1.0.0
 */
public class RocksDBLongTermTaskStore implements LongTermTaskStore, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(RocksDBLongTermTaskStore.class);

  private static final String CF_TIME_INDEX = "time_index";
  private static final String CF_ID_INDEX = "id_index";

  private final RocksDB db;
  private final DBOptions dbOptions;
  private final ColumnFamilyOptions cfOptions;
  private final ColumnFamilyHandle defaultHandle;
  private final ColumnFamilyHandle timeIndexHandle;
  private final ColumnFamilyHandle idIndexHandle;

  static {
    RocksDB.loadLibrary();
  }

  /**
   * Constructs a new RocksDB store using the provided configuration.
   *
   * @param serverConfig the configuration for RocksDB path and behavior; must be non-null
   * @throws StorageException if RocksDB fails to initialize
   */
  public RocksDBLongTermTaskStore(ServerConfig serverConfig) {
    String dbPath = serverConfig.getRocksDbPath();
    try {
      Files.createDirectories(Paths.get(dbPath));

      this.dbOptions =
          new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
      this.cfOptions = new ColumnFamilyOptions();

      List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
      cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));
      cfDescriptors.add(new ColumnFamilyDescriptor(CF_TIME_INDEX.getBytes(), cfOptions));
      cfDescriptors.add(new ColumnFamilyDescriptor(CF_ID_INDEX.getBytes(), cfOptions));

      List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
      this.db = RocksDB.open(dbOptions, dbPath, cfDescriptors, cfHandles);
      this.defaultHandle = cfHandles.get(0);
      this.timeIndexHandle = cfHandles.get(1);
      this.idIndexHandle = cfHandles.get(2);

      log.info("Initialized RocksDB long-term store at {}", dbPath);
    } catch (IOException | RocksDBException e) {
      log.error("Failed to initialize RocksDB at {}", dbPath, e);
      throw new StorageException("Could not initialize RocksDB long-term store at " + dbPath, e);
    }
  }

  @Override
  public void save(TimerTask task) {
    byte[] taskIdBytes = task.getTaskId().getBytes();
    byte[] expirationBytes = longToBytes(task.getExpirationMs());

    try (WriteBatch batch = new WriteBatch();
        WriteOptions writeOptions = new WriteOptions()) {
      // 1. Check if task already exists in id_index
      byte[] oldExpirationBytes = db.get(idIndexHandle, taskIdBytes);
      if (oldExpirationBytes != null) {
        // Remove old entry from time_index
        byte[] oldTimeKey = createTimeKey(oldExpirationBytes, taskIdBytes);
        batch.delete(timeIndexHandle, oldTimeKey);
      }

      // 2. Add to time_index
      byte[] timeKey = createTimeKey(expirationBytes, taskIdBytes);
      byte[] serializedTask = TimerTaskSerializer.serialize(task);
      batch.put(timeIndexHandle, timeKey, serializedTask);

      // 3. Add to id_index
      batch.put(idIndexHandle, taskIdBytes, expirationBytes);

      db.write(writeOptions, batch);
    } catch (RocksDBException | IOException e) {
      log.error("Failed to save task {} to RocksDB", task.getTaskId(), e);
      throw new StorageException("Persistence error during task save for " + task.getTaskId(), e);
    }
  }

  @Override
  public Collection<TimerTask> fetchTasksDueBefore(long timestamp) {
    List<TimerTask> dueTasks = new ArrayList<>();
    byte[] upperBound = longToBytes(timestamp);

    try (RocksIterator iter = db.newIterator(timeIndexHandle)) {
      for (iter.seekToFirst(); iter.isValid(); iter.next()) {
        byte[] key = iter.key();
        if (isAfter(key, upperBound)) {
          break;
        }

        try {
          TimerTask task = TimerTaskSerializer.deserialize(iter.value());
          dueTasks.add(task);
        } catch (IOException e) {
          log.warn("Failed to deserialize task during fetch, skipping", e);
        }
      }
    }

    return dueTasks;
  }

  @Override
  public Optional<TimerTask> findById(String taskId) {
    byte[] taskIdBytes = taskId.getBytes();
    try {
      byte[] expirationBytes = db.get(idIndexHandle, taskIdBytes);
      if (expirationBytes == null) {
        return Optional.empty();
      }

      byte[] timeKey = createTimeKey(expirationBytes, taskIdBytes);
      byte[] taskData = db.get(timeIndexHandle, timeKey);
      if (taskData == null) {
        // This should not happen if indexes are in sync
        log.error("Integrity error: task {} found in id_index but missing in time_index", taskId);
        return Optional.empty();
      }

      return Optional.of(TimerTaskSerializer.deserialize(taskData));
    } catch (RocksDBException | IOException e) {
      log.error("Failed to find task {} in RocksDB", taskId, e);
      throw new StorageException("Persistence error during task lookup for " + taskId, e);
    }
  }

  @Override
  public void delete(TimerTask task) {
    byte[] taskIdBytes = task.getTaskId().getBytes();
    byte[] expirationBytes = longToBytes(task.getExpirationMs());
    byte[] timeKey = createTimeKey(expirationBytes, taskIdBytes);

    try (WriteBatch batch = new WriteBatch();
        WriteOptions writeOptions = new WriteOptions()) {
      batch.delete(timeIndexHandle, timeKey);
      batch.delete(idIndexHandle, taskIdBytes);
      db.write(writeOptions, batch);
    } catch (RocksDBException e) {
      log.error("Failed to delete task {} from RocksDB", task.getTaskId(), e);
      throw new StorageException(
          "Persistence error during task deletion for " + task.getTaskId(), e);
    }
  }

  @Override
  public void close() {
    timeIndexHandle.close();
    idIndexHandle.close();
    defaultHandle.close();
    db.close();
    dbOptions.close();
    cfOptions.close();
  }

  private byte[] longToBytes(long value) {
    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
  }

  private byte[] createTimeKey(byte[] expirationBytes, byte[] taskIdBytes) {
    byte[] key = new byte[expirationBytes.length + taskIdBytes.length];
    System.arraycopy(expirationBytes, 0, key, 0, expirationBytes.length);
    System.arraycopy(taskIdBytes, 0, key, expirationBytes.length, taskIdBytes.length);
    return key;
  }

  private boolean isAfter(byte[] key, byte[] upperBound) {
    // Compare only the first 8 bytes (expirationMs)
    for (int i = 0; i < Long.BYTES; i++) {
      int b1 = key[i] & 0xFF;
      int b2 = upperBound[i] & 0xFF;
      if (b1 > b2) return true;
      if (b1 < b2) return false;
    }
    return false; // Equal is not "after"
  }
}
