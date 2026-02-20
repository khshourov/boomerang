package io.boomerang.timer;

import java.util.Collection;

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
   * @param task the task to save; must be non-null
   */
  void save(TimerTask task);

  /**
   * Fetches all tasks due on or before the specified timestamp.
   *
   * @param timestamp the deadline for tasks to be fetched
   * @return a collection of tasks due before the timestamp
   */
  Collection<TimerTask> fetchTasksDueBefore(long timestamp);

  /**
   * Deletes a task from the store.
   *
   * @param task the task to delete; must be non-null
   */
  void delete(TimerTask task);
}
