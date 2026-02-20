package io.boomerang.timer;

import java.util.Collection;
import java.util.Optional;

/**
 * Interface for a persistent store for dead-letter tasks.
 *
 * <p>Tasks that have exhausted their retry attempts are moved to the DLQ for manual inspection or
 * replay.
 *
 * @since 1.0.0
 */
public interface DLQStore {

  /**
   * Represents an entry in the Dead Letter Queue.
   *
   * @param task the failed task
   * @param errorMessage the description of the failure
   */
  record DLQEntry(TimerTask task, String errorMessage) {}

  /**
   * Saves an entry to the dead-letter queue.
   *
   * @param entry the entry to save; must be non-null
   */
  void save(DLQEntry entry);

  /**
   * Retrieves all entries currently in the dead-letter queue.
   *
   * @return a collection of all entries in the DLQ
   */
  Collection<DLQEntry> findAll();

  /**
   * Retrieves all entries in the dead-letter queue for a specific client.
   *
   * @param clientId the identifier of the client
   * @return a collection of entries for the specified client
   */
  Collection<DLQEntry> findAll(String clientId);

  /**
   * Retrieves a specific entry from the DLQ by client and task identifiers.
   *
   * @param clientId the identifier of the client
   * @param taskId the unique ID of the task
   * @return an {@link Optional} containing the entry if found, or empty otherwise
   */
  Optional<DLQEntry> findEntryById(String clientId, String taskId);

  /**
   * Removes an entry from the dead-letter queue.
   *
   * @param clientId the identifier of the client
   * @param taskId the unique ID of the task to remove
   */
  void delete(String clientId, String taskId);

  /** Clears all entries from the dead-letter queue. */
  void clear();
}
