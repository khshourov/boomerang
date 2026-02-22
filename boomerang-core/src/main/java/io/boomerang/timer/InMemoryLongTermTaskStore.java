package io.boomerang.timer;

import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

  @Override
  public synchronized ListResult<TimerTask> list(
      String clientId,
      long scheduledAfter,
      long scheduledBefore,
      Boolean isRecurring,
      int limit,
      String nextToken) {
    var stream =
        timeIndex.subMap(scheduledAfter, true, scheduledBefore, true).values().stream()
            .flatMap(Collection::stream)
            .sorted(
                (t1, t2) -> {
                  int cmp = Long.compare(t1.getExpirationMs(), t2.getExpirationMs());
                  if (cmp == 0) return t1.getTaskId().compareTo(t2.getTaskId());
                  return cmp;
                });

    if (clientId != null) {
      stream = stream.filter(task -> clientId.equals(task.getClientId()));
    }
    if (isRecurring != null) {
      stream = stream.filter(task -> (task.getRepeatIntervalMs() > 0) == isRecurring);
    }

    if (nextToken != null && !nextToken.isEmpty()) {
      stream =
          stream.dropWhile(
              t -> {
                String currentToken = t.getExpirationMs() + "_" + t.getTaskId();
                return currentToken.compareTo(nextToken) <= 0;
              });
    }

    var tasks = stream.limit(limit).collect(Collectors.toList());
    String nextCursor = null;
    if (!tasks.isEmpty() && tasks.size() == limit) {
      var last = tasks.getLast();
      nextCursor = last.getExpirationMs() + "_" + last.getTaskId();
    }

    return new ListResult<>(tasks, nextCursor);
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
