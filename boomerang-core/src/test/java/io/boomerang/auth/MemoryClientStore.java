package io.boomerang.auth;

import io.boomerang.model.Client;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ClientStore} for testing purposes.
 *
 * @since 1.0.0
 */
public class MemoryClientStore implements ClientStore {
  private final Map<String, Client> clients = new ConcurrentHashMap<>();

  @Override
  public void save(Client client) {
    clients.put(client.clientId(), client);
  }

  @Override
  public Optional<Client> findById(String clientId) {
    return Optional.ofNullable(clients.get(clientId));
  }

  @Override
  public void delete(String clientId) {
    clients.remove(clientId);
  }

  @Override
  public void close() {
    clients.clear();
  }
}
