package io.boomerang.timer;

import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple in-memory implementation of {@link LongTermTaskStore} for testing and initial
 * development.
 *
 * <p>This implementation uses a {@link TreeMap} to maintain tasks sorted by expiration time, and a
 * {@link Map} to maintain an index by {@code taskId} for efficient retrieval.
 *
 * @since 1.0.0
 */
public class InMemoryLongTermTaskStore implements LongTermTaskStore {
  private final NavigableMap<Long, Collection<TimerTask>> timeIndex = new TreeMap<>();
  private final Map<String, TimerTask> idIndex = new ConcurrentHashMap<>();

  @Override
  public synchronized void save(TimerTask task) {
    // If a task with the same ID already exists, remove it first from the time index
    TimerTask existing = idIndex.put(task.getTaskId(), task);
    if (existing != null) {
      removeFromTimeIndex(existing);
    }

    timeIndex
        .computeIfAbsent(
            task.getExpirationMs(), k -> new java.util.concurrent.ConcurrentLinkedQueue<>())
        .add(task);
  }

  @Override
  public synchronized Collection<TimerTask> fetchTasksDueBefore(long timestamp) {
    NavigableMap<Long, Collection<TimerTask>> subMap = timeIndex.headMap(timestamp, true);
    return subMap.values().stream().flatMap(Collection::stream).toList();
  }

  @Override
  public synchronized Optional<TimerTask> findById(String taskId) {
    return Optional.ofNullable(idIndex.get(taskId));
  }

  @Override
  public synchronized void delete(TimerTask task) {
    idIndex.remove(task.getTaskId());
    removeFromTimeIndex(task);
  }

  private void removeFromTimeIndex(TimerTask task) {
    Collection<TimerTask> tasks = timeIndex.get(task.getExpirationMs());
    if (tasks != null) {
      tasks.remove(task);
      if (tasks.isEmpty()) {
        timeIndex.remove(task.getExpirationMs());
      }
    }
  }
}
