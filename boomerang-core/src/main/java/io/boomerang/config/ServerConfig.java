package io.boomerang.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and provides access to server-level configuration properties.
 *
 * <p>This class attempts to load properties from a file-system path, falling back to a classpath
 * resource named {@code boomerang-server.properties} if the path is invalid or null.
 *
 * @since 1.0.0
 */
public class ServerConfig {
  private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);
  private final Properties properties = new Properties();

  /**
   * Constructs a server configuration by loading properties from the given path.
   *
   * @param configPath the filesystem path to the properties file; can be {@code null} or empty to
   *     use classpath defaults
   */
  public ServerConfig(String configPath) {
    if (configPath == null || configPath.isEmpty()) {
      loadFromClasspath();
      return;
    }

    try (FileInputStream fis = new FileInputStream(configPath)) {
      properties.load(fis);
    } catch (IOException e) {
      log.warn("Could not load config from {}: {}. Using defaults.", configPath, e.getMessage());
      loadFromClasspath();
    }
  }

  /**
   * Loads the server configuration from the path specified in the environment or system
   * properties.
   *
   * @return a new {@link ServerConfig} instance
   */
  public static ServerConfig load() {
    String path = System.getenv("BOOMERANG_CONFIG");
    if (path == null || path.isEmpty()) {
      path = System.getProperty("BOOMERANG_CONFIG");
    }
    return new ServerConfig(path);
  }

  private void loadFromClasspath() {
    try (var is = getClass().getClassLoader().getResourceAsStream("boomerang-server.properties")) {
      if (is != null) {
        properties.load(is);
        log.info("Loaded config from classpath: boomerang-server.properties");
      }
    } catch (IOException e) {
      log.warn("Could not load config from classpath: {}", e.getMessage());
    }
  }

  private String getProperty(String key, String defaultValue) {
    String value = System.getProperty(key);
    if (value == null) {
      value = properties.getProperty(key, defaultValue);
    }
    return value;
  }

  /**
   * Gets the administrative client identifier.
   *
   * @return the admin client ID
   */
  public String getAdminClientId() {
    return getProperty("admin.client.id", "admin");
  }

  /**
   * Gets the administrative password.
   *
   * @return the admin password
   */
  public String getAdminPassword() {
    // TODO: Implement decryption if the file is encrypted.
    return getProperty("admin.password", "admin123");
  }

  /**
   * Gets the session timeout duration in minutes.
   *
   * @return the session timeout in minutes
   */
  public long getSessionTimeoutMinutes() {
    return Long.parseLong(getProperty("session.timeout.minutes", "60"));
  }

  /**
   * Gets the interval at which the timer's clock should be advanced.
   *
   * @return the clock advancement interval in milliseconds
   */
  public long getTimerAdvanceClockIntervalMs() {
    return Long.parseLong(getProperty("timer.advance.clock.interval.ms", "200"));
  }

  /**
   * Gets the duration of a single tick in the innermost timing wheel.
   *
   * @return the tick duration in milliseconds
   */
  public long getTimerTickMs() {
    return Long.parseLong(getProperty("timer.tick.ms", "10"));
  }

  /**
   * Gets the number of buckets in each level of the timing wheel.
   *
   * @return the wheel size
   */
  public int getTimerWheelSize() {
    return Integer.parseInt(getProperty("timer.wheel.size", "64"));
  }

  /**
   * Gets the time window for tasks to be kept in memory.
   *
   * @return the imminent window in milliseconds
   */
  public long getTimerImminentWindowMs() {
    // Default to 30 minutes
    return Long.parseLong(getProperty("timer.imminent.window.ms", "1800000"));
  }

  /**
   * Checks if RocksDB persistence is enabled.
   *
   * @return {@code true} if RocksDB is enabled, {@code false} otherwise
   */
  public boolean isRocksDbEnabled() {
    return Boolean.parseBoolean(getProperty("rocksdb.enabled", "true"));
  }

  /**
   * Gets the filesystem path for the RocksDB data directory.
   *
   * @return the RocksDB path
   */
  public String getRocksDbPath() {
    return getProperty("rocksdb.path", "data/rocksdb");
  }

  /**
   * Gets the filesystem path for the RocksDB client storage directory.
   *
   * @return the RocksDB client path
   */
  public String getRocksDbClientPath() {
    return getProperty("rocksdb.client.path", "data/clients");
  }

  /**
   * Gets the filesystem path for the RocksDB dead-letter queue (DLQ) directory.
   *
   * @return the RocksDB DLQ path
   */
  public String getRocksDbDlqPath() {
    return getProperty("rocksdb.dlq.path", "data/dlq");
  }

  /**
   * Gets the master key for AES encryption.
   *
   * <p>This key is retrieved from the {@code BOOMERANG_MASTER_KEY} environment variable or system
   * property.
   *
   * @return the encryption master key, or null if not set
   */
  public String getEncryptionMasterKey() {
    String key = System.getenv("BOOMERANG_MASTER_KEY");
    if (key == null || key.isEmpty()) {
      key = System.getProperty("BOOMERANG_MASTER_KEY");
    }
    return key;
  }

  /**
   * Gets the TCP server port.
   *
   * @return the server port
   */
  public int getServerPort() {
    return Integer.parseInt(getProperty("server.port", "9973"));
  }

  /**
   * Gets the number of threads for the Netty boss event loop group.
   *
   * @return the number of boss threads
   */
  public int getNettyBossThreads() {
    return Integer.parseInt(getProperty("netty.boss.threads", "1"));
  }

  /**
   * Gets the number of threads for the Netty worker event loop group.
   *
   * @return the number of worker threads
   */
  public int getNettyWorkerThreads() {
    return Integer.parseInt(getProperty("netty.worker.threads", "0"));
  }

  /**
   * Gets the number of threads for the business logic thread pool.
   *
   * @return the number of business threads
   */
  public int getNettyBusinessThreads() {
    return Integer.parseInt(
        getProperty(
            "netty.business.threads",
            String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() * 2))));
  }

  /**
   * Gets the number of threads for the callback execution pool.
   *
   * @return the number of callback threads
   */
  public int getCallbackThreads() {
    return Integer.parseInt(
        getProperty(
            "callback.threads",
            String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() * 4))));
  }

  /**
   * Gets the timeout for TCP callbacks.
   *
   * @return the TCP callback timeout in milliseconds
   */
  public long getCallbackTcpTimeoutMs() {
    return Long.parseLong(getProperty("callback.tcp.timeout.ms", "1000"));
  }

  /**
   * Gets the timeout for HTTP callbacks.
   *
   * @return the HTTP callback timeout in milliseconds
   */
  public long getCallbackHttpTimeoutMs() {
    return Long.parseLong(getProperty("callback.http.timeout.ms", "2000"));
  }

  /**
   * Gets the timeout for gRPC callbacks.
   *
   * @return the gRPC callback timeout in milliseconds
   */
  public long getCallbackGrpcTimeoutMs() {
    return Long.parseLong(getProperty("callback.grpc.timeout.ms", "3000"));
  }

  /**
   * Gets the maximum number of concurrent connections per endpoint for TCP callbacks.
   *
   * @return the maximum TCP connections per endpoint
   */
  public int getCallbackTcpPoolMaxConnections() {
    return Integer.parseInt(getProperty("callback.tcp.pool.max.connections", "50"));
  }

  /**
   * Gets the maximum number of cached gRPC channels.
   *
   * @return the maximum gRPC channels
   */
  public int getCallbackGrpcPoolMaxChannels() {
    return Integer.parseInt(getProperty("callback.grpc.pool.max.channels", "100"));
  }

  /**
   * Gets the idle timeout for gRPC channels.
   *
   * @return the gRPC idle timeout in milliseconds
   */
  public long getCallbackGrpcIdleTimeoutMs() {
    return Long.parseLong(getProperty("callback.grpc.idle.timeout.ms", "60000"));
  }
}
