package io.boomerang.server.callback;

import io.boomerang.auth.ClientStore;
import io.boomerang.model.CallbackConfig;
import io.boomerang.model.Client;
import io.boomerang.timer.TimerTask;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link CallbackDispatcher} that routes tasks to protocol-specific
 * handlers.
 *
 * @since 1.0.0
 */
public class DefaultCallbackDispatcher implements CallbackDispatcher {
  private static final Logger log = LoggerFactory.getLogger(DefaultCallbackDispatcher.class);

  private final ClientStore clientStore;
  private final Map<CallbackConfig.Protocol, CallbackHandler> handlers;

  /**
   * Constructs a new dispatcher with a collection of protocol handlers.
   *
   * @param clientStore the store for client callback configurations; must be non-null
   * @param handlers the protocol-specific handlers; must be non-null
   */
  public DefaultCallbackDispatcher(ClientStore clientStore, Collection<CallbackHandler> handlers) {
    this.clientStore = Objects.requireNonNull(clientStore, "clientStore must not be null");
    this.handlers = new ConcurrentHashMap<>();
    handlers.forEach(h -> this.handlers.put(h.getProtocol(), h));
  }

  @Override
  public void dispatch(TimerTask task) throws CallbackException {
    Client client =
        clientStore
            .findById(task.getClientId())
            .orElseThrow(() -> new CallbackException("Unknown client: " + task.getClientId()));

    CallbackConfig config = client.callbackConfig();
    if (config == null) {
      throw new CallbackException("No callback configuration for client: " + task.getClientId());
    }

    CallbackHandler handler = handlers.get(config.protocol());
    if (handler == null) {
      throw new CallbackException("Unsupported protocol: " + config.protocol());
    }

    log.debug(
        "Dispatching task {} to client {} via {} to {}",
        task.getTaskId(),
        task.getClientId(),
        config.protocol(),
        config.endpoint());

    handler.handle(task, config);
  }

  @Override
  public void shutdown() {
    log.info("Shutting down CallbackDispatcher...");
    handlers.values().forEach(CallbackHandler::shutdown);
    handlers.clear();
  }
}
