package io.boomerang.timer;

import java.util.function.Consumer;

/**
 * Factory for creating different timer implementations.
 *
 * @since 1.0.0
 */
public class TimerFactory {
  private TimerFactory() {}

  /**
   * Creates a {@link HierarchicalTimingWheel} with default configuration.
   *
   * @param dispatcher a consumer for expired tasks; must be non-null
   * @return a new {@link Timer} instance
   */
  public static Timer createHierarchicalTimingWheel(Consumer<TimerTask> dispatcher) {
    return new HierarchicalTimingWheel(10, 64, dispatcher, null);
  }

  /**
   * Creates a {@link HierarchicalTimingWheel} with custom configuration.
   *
   * @param tickMs the duration of a single tick; must be positive
   * @param wheelSize the number of buckets in each wheel; must be positive
   * @param dispatcher a consumer for expired tasks; must be non-null
   * @return a new {@link Timer} instance
   */
  public static Timer createHierarchicalTimingWheel(
      long tickMs, int wheelSize, Consumer<TimerTask> dispatcher) {
    return new HierarchicalTimingWheel(tickMs, wheelSize, dispatcher, null);
  }

  /**
   * Creates a {@link TieredTimer} with the specified configuration.
   *
   * @param dispatcher the consumer for expired tasks; must be non-null
   * @param longTermStore the store for long-term tasks; must be non-null
   * @param serverConfig the server configuration for timer tuning; must be non-null
   * @return a new {@link Timer} instance
   */
  public static Timer createTieredTimer(
      Consumer<TimerTask> dispatcher,
      LongTermTaskStore longTermStore,
      io.boomerang.config.ServerConfig serverConfig) {
    return new TieredTimer(dispatcher, longTermStore, serverConfig);
  }

  /**
   * Creates a {@link TieredTimer} with a store selected based on server configuration.
   *
   * @param dispatcher the consumer for expired tasks; must be non-null
   * @param serverConfig the server configuration; must be non-null
   * @return a new {@link Timer} instance
   */
  public static Timer createTieredTimer(
      Consumer<TimerTask> dispatcher, io.boomerang.config.ServerConfig serverConfig) {
    LongTermTaskStore store;
    if (serverConfig.isRocksDbEnabled()) {
      store = new RocksDBLongTermTaskStore(serverConfig);
    } else {
      store = new InMemoryLongTermTaskStore();
    }
    return new TieredTimer(dispatcher, store, serverConfig);
  }
}
