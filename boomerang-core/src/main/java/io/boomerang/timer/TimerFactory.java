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
    return new HierarchicalTimingWheel(10, 64, dispatcher);
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
    return new HierarchicalTimingWheel(tickMs, wheelSize, dispatcher);
  }
}
