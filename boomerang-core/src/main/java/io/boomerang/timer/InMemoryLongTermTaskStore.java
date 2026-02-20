package io.boomerang.timer;

import java.util.Collection;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A simple in-memory implementation of {@link LongTermTaskStore} for testing and initial
 * development.
 *
 * <p>This implementation uses a {@link TreeMap} to maintain tasks sorted by expiration time,
 * allowing efficient retrieval of tasks within a specific window.
 *
 * @since 1.0.0
 */
public class InMemoryLongTermTaskStore implements LongTermTaskStore {
  private final NavigableMap<Long, Collection<TimerTask>> store = new TreeMap<>();

  @Override
  public synchronized void save(TimerTask task) {
    store
        .computeIfAbsent(
            task.getExpirationMs(), k -> new java.util.concurrent.ConcurrentLinkedQueue<>())
        .add(task);
  }

  @Override
  public synchronized Collection<TimerTask> fetchTasksDueBefore(long timestamp) {
    NavigableMap<Long, Collection<TimerTask>> subMap = store.headMap(timestamp, true);
    Collection<TimerTask> tasks = subMap.values().stream().flatMap(Collection::stream).toList();
    subMap.clear(); // Tasks are removed once fetched for transition
    return tasks;
  }

  @Override
  public synchronized void delete(TimerTask task) {
    Collection<TimerTask> tasks = store.get(task.getExpirationMs());
    if (tasks != null) {
      tasks.remove(task);
      if (tasks.isEmpty()) {
        store.remove(task.getExpirationMs());
      }
    }
  }
}
