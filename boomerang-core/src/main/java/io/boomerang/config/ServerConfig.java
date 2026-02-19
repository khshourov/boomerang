package io.boomerang.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerConfig {
  private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);
  private final Properties properties = new Properties();

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

  public String getAdminClientId() {
    return properties.getProperty("admin.client.id", "admin");
  }

  public String getAdminPassword() {
    // TODO: Implement decryption if the file is encrypted.
    return properties.getProperty("admin.password", "admin123");
  }

  public long getSessionTimeoutMinutes() {
    return Long.parseLong(properties.getProperty("session.timeout.minutes", "60"));
  }

  public long getTimerAdvanceClockIntervalMs() {
    return Long.parseLong(properties.getProperty("timer.advance.clock.interval.ms", "200"));
  }
}
