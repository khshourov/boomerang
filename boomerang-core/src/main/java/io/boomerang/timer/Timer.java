package io.boomerang.timer;

public interface Timer {
  void add(TimerTask task);

  void shutdown();
}
