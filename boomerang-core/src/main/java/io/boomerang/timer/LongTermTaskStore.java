package io.boomerang.timer;

import java.util.Collection;
import java.util.Optional;

/**
 * Interface for a persistent or temporary store for tasks scheduled for the distant future.
 *
 * <p>Tasks that are not currently being managed by an in-memory timing wheel are stored here until
 * their execution window approaches.
 *
 * @since 1.0.0
 */
public interface LongTermTaskStore {
  /**
   * Saves a task to the long-term store.
   *
   * <p>If a task with the same {@code taskId} already exists, it will be overwritten.
   *
   * @param task the task to save; must be non-null
   */
  void save(TimerTask task);

  /**
   * Fetches all tasks due on or before the specified timestamp.
   *
   * @param timestamp the deadline for tasks to be fetched; must be non-negative
   * @return a collection of tasks due before the timestamp; never {@code null}
   */
  Collection<TimerTask> fetchTasksDueBefore(long timestamp);

  /**
   * Finds a task by its unique identifier.
   *
   * @param taskId the unique ID of the task to find; must be non-null
   * @return an {@link Optional} containing the task if found, or empty otherwise
   */
  Optional<TimerTask> findById(String taskId);

  /**
   * Deletes a task from the store.
   *
   * <p>The task is identified by its {@code taskId} and {@code expirationMs}.
   *
   * @param task the task to delete; must be non-null
   */
  void delete(TimerTask task);

  /**
   * Lists tasks based on the provided filters and pagination.
   *
   * @param clientId filter by client; if null, lists tasks for all clients (admin)
   * @param scheduledAfter start of interval (Unix epoch ms)
   * @param scheduledBefore end of interval (Unix epoch ms)
   * @param isRecurring filter by one-shot (false) vs recurring (true); null means both
   * @param limit max tasks to return
   * @param nextToken opaque cursor for pagination
   * @return a result containing the tasks and the next cursor
   */
  ListResult<TimerTask> list(
      String clientId,
      long scheduledAfter,
      long scheduledBefore,
      Boolean isRecurring,
      int limit,
      String nextToken);
}
