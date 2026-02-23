package io.boomerang.client;

import io.boomerang.proto.GetTaskResponse;
import io.boomerang.proto.ListTasksRequest;
import io.boomerang.proto.ListTasksResponse;
import io.boomerang.proto.RegistrationResponse;
import io.boomerang.proto.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for {@link BoomerangClient} that provides reliability features like automatic re-login.
 *
 * @since 0.1.0
 */
public class SmartBoomerangClient implements BoomerangClient {
  private static final Logger log = LoggerFactory.getLogger(SmartBoomerangClient.class);
  private final BoomerangClient delegate;
  private final String clientId;
  private final String password;
  private boolean loggedIn = false;

  public SmartBoomerangClient(BoomerangClient delegate, String clientId, String password) {
    this.delegate = delegate;
    this.clientId = clientId;
    this.password = password;
  }

  @Override
  public void connect() throws BoomerangException {
    delegate.connect();
    ensureLoggedIn();
  }

  @Override
  public void login(String clientId, String password) throws BoomerangException {
    delegate.login(clientId, password);
    loggedIn = true;
  }

  private void ensureLoggedIn() throws BoomerangException {
    if (!loggedIn) {
      log.info("Attempting automatic login for clientId: {}", clientId);
      login(clientId, password);
    }
  }

  @Override
  public RegistrationResponse register(Task task) throws BoomerangException {
    return executeWithRetry(() -> delegate.register(task));
  }

  @Override
  public boolean cancel(String taskId) throws BoomerangException {
    return executeWithRetry(() -> delegate.cancel(taskId));
  }

  @Override
  public GetTaskResponse getTask(String taskId) throws BoomerangException {
    return executeWithRetry(() -> delegate.getTask(taskId));
  }

  @Override
  public ListTasksResponse listTasks(ListTasksRequest request) throws BoomerangException {
    return executeWithRetry(() -> delegate.listTasks(request));
  }

  private <T> T executeWithRetry(ClientAction<T> action) throws BoomerangException {
    try {
      ensureLoggedIn();
      return action.execute();
    } catch (BoomerangException e) {
      if (e.getMessage().contains("SESSION_EXPIRED") || e.getMessage().contains("UNAUTHORIZED")) {
        log.warn("Session expired or unauthorized. Attempting to re-login and retry...");
        loggedIn = false;
        ensureLoggedIn();
        return action.execute();
      }
      throw e;
    }
  }

  @FunctionalInterface
  private interface ClientAction<T> {
    T execute() throws BoomerangException;
  }

  @Override
  public void close() {
    delegate.close();
  }
}
