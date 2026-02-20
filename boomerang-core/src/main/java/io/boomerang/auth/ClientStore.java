package io.boomerang.auth;

import io.boomerang.model.Client;
import java.util.Optional;

/**
 * Interface for persistent storage of client credentials and metadata.
 *
 * <p>Implementations of this interface are responsible for persisting and retrieving {@link Client}
 * records, ensuring they are protected against unauthorized access.
 *
 * @since 1.0.0
 */
public interface ClientStore extends AutoCloseable {

  /**
   * Persists a client record.
   *
   * @param client the client to save; must be non-null
   */
  void save(Client client);

  /**
   * Retrieves a client by their unique identifier.
   *
   * @param clientId the identifier of the client to find; must be non-null
   * @return an {@link Optional} containing the {@link Client} if found, or empty if not
   */
  Optional<Client> findById(String clientId);

  /**
   * Deletes a client record from the store.
   *
   * @param clientId the identifier of the client to delete; must be non-null
   */
  void delete(String clientId);
}
