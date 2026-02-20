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

  /**
   * Gets the administrative client identifier.
   *
   * @return the admin client ID
   */
  public String getAdminClientId() {
    return properties.getProperty("admin.client.id", "admin");
  }

  /**
   * Gets the administrative password.
   *
   * @return the admin password
   */
  public String getAdminPassword() {
    // TODO: Implement decryption if the file is encrypted.
    return properties.getProperty("admin.password", "admin123");
  }

  /**
   * Gets the session timeout duration in minutes.
   *
   * @return the session timeout in minutes
   */
  public long getSessionTimeoutMinutes() {
    return Long.parseLong(properties.getProperty("session.timeout.minutes", "60"));
  }

  /**
   * Gets the interval at which the timer's clock should be advanced.
   *
   * @return the clock advancement interval in milliseconds
   */
  public long getTimerAdvanceClockIntervalMs() {
    return Long.parseLong(properties.getProperty("timer.advance.clock.interval.ms", "200"));
  }

  /**
   * Gets the duration of a single tick in the innermost timing wheel.
   *
   * @return the tick duration in milliseconds
   */
  public long getTimerTickMs() {
    return Long.parseLong(properties.getProperty("timer.tick.ms", "10"));
  }

  /**
   * Gets the number of buckets in each level of the timing wheel.
   *
   * @return the wheel size
   */
  public int getTimerWheelSize() {
    return Integer.parseInt(properties.getProperty("timer.wheel.size", "64"));
  }

  /**
   * Gets the time window for tasks to be kept in memory.
   *
   * @return the imminent window in milliseconds
   */
  public long getTimerImminentWindowMs() {
    // Default to 30 minutes
    return Long.parseLong(properties.getProperty("timer.imminent.window.ms", "1800000"));
  }

  /**
   * Checks if RocksDB persistence is enabled.
   *
   * @return {@code true} if RocksDB is enabled, {@code false} otherwise
   */
  public boolean isRocksDbEnabled() {
    return Boolean.parseBoolean(properties.getProperty("rocksdb.enabled", "true"));
  }

  /**
   * Gets the filesystem path for the RocksDB data directory.
   *
   * @return the RocksDB path
   */
  public String getRocksDbPath() {
    return properties.getProperty("rocksdb.path", "data/rocksdb");
  }
}
