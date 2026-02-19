package io.boomerang.timer;

import java.util.function.Consumer;

public class TimerFactory {
  private TimerFactory() {}

  public static Timer createHierarchicalTimingWheel(Consumer<TimerTask> dispatcher) {
    return new HierarchicalTimingWheel(10, 64, dispatcher);
  }

  public static Timer createHierarchicalTimingWheel(
      long tickMs, int wheelSize, Consumer<TimerTask> dispatcher) {
    return new HierarchicalTimingWheel(tickMs, wheelSize, dispatcher);
  }
}
