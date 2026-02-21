package io.boomerang;

import io.boomerang.auth.AuthService;
import io.boomerang.auth.ClientStore;
import io.boomerang.auth.RocksDBClientStore;
import io.boomerang.config.ServerConfig;
import io.boomerang.server.BoomerangServer;
import io.boomerang.server.callback.CallbackDispatcher;
import io.boomerang.server.callback.DefaultCallbackDispatcher;
import io.boomerang.server.callback.GrpcCallbackHandler;
import io.boomerang.server.callback.HttpCallbackHandler;
import io.boomerang.server.callback.TcpCallbackHandler;
import io.boomerang.server.callback.UdpCallbackHandler;
import io.boomerang.session.SessionManager;
import io.boomerang.timer.DLQStore;
import io.boomerang.timer.DefaultRetryEngine;
import io.boomerang.timer.LongTermTaskStore;
import io.boomerang.timer.RetryEngine;
import io.boomerang.timer.RocksDBDLQStore;
import io.boomerang.timer.RocksDBLongTermTaskStore;
import io.boomerang.timer.TieredTimer;
import io.boomerang.timer.Timer;
import io.boomerang.timer.TimerTask;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps the Boomerang server by initializing core services and configurations.
 *
 * <p>This class coordinates the startup sequence, including registering the administrative client
 * and setting up auth and session services, as well as the task scheduling engine.
 *
 * @since 1.0.0
 */
public class BoomerangBootstrap {
  private static final Logger log = LoggerFactory.getLogger(BoomerangBootstrap.class);
  private final AuthService authService;
  private final SessionManager sessionManager;
  private final ServerConfig serverConfig;
  private final ClientStore clientStore;
  private final LongTermTaskStore taskStore;
  private final DLQStore dlqStore;
  private final RetryEngine retryEngine;
  private final CallbackDispatcher callbackDispatcher;
  private final Timer timer;
  private final BoomerangServer server;

  /**
   * Constructs a bootstrap instance with the provided server configuration.
   *
   * @param serverConfig the server configuration to use; must be non-null
   */
  public BoomerangBootstrap(ServerConfig serverConfig) {
    this.serverConfig = serverConfig;
    this.clientStore = new RocksDBClientStore(serverConfig);
    this.sessionManager = new SessionManager(serverConfig);
    this.authService = new AuthService(clientStore, serverConfig, sessionManager);

    // Initialize task storage and scheduling engine
    this.taskStore = new RocksDBLongTermTaskStore(serverConfig);
    this.dlqStore = new RocksDBDLQStore(serverConfig);

    // Initialize callback engine
    this.callbackDispatcher =
        new DefaultCallbackDispatcher(
            clientStore,
            List.of(
                new TcpCallbackHandler(serverConfig.getCallbackTcpTimeoutMs()),
                new HttpCallbackHandler(Duration.ofMillis(serverConfig.getCallbackHttpTimeoutMs())),
                new UdpCallbackHandler(),
                new GrpcCallbackHandler(serverConfig.getCallbackGrpcTimeoutMs())));

    // The retry engine needs to reschedule tasks using the timer
    this.retryEngine = new DefaultRetryEngine(clientStore, taskStore, dlqStore, this::resubmitTask);

    this.timer =
        new TieredTimer(
            task -> {
              try {
                // Dispatch the task to the registered callback endpoint
                callbackDispatcher.dispatch(task);

                // Task successfully dispatched (or handled by external engine)
                taskStore.delete(task);

                // Automatic rescheduling for repeatable tasks only on SUCCESSFUL dispatch.
                if (task.getRepeatIntervalMs() > 0) {
                  resubmitTask(task.nextCycle());
                }
              } catch (Exception e) {
                log.error("Failed to dispatch task {}: {}", task.getTaskId(), e.getMessage());
                retryEngine.handleFailure(task, e);
              }
            },
            taskStore,
            serverConfig);

    this.server = new BoomerangServer(serverConfig, authService, sessionManager, timer);
  }

  private void resubmitTask(TimerTask task) {
    if (timer != null) {
      timer.add(task);
    }
  }

  /** Starts the Boomerang core services. */
  public void start() {
    log.info("Starting Boomerang core...");
    try {
      server.start();
    } catch (InterruptedException e) {
      log.error("Failed to start Boomerang server", e);
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Closes the Boomerang core services and releases resources.
   *
   * @throws Exception if an error occurs during shutdown
   */
  public void close() throws Exception {
    log.info("Stopping Boomerang core...");
    if (server != null) {
      server.stop();
    }
    if (timer != null) {
      timer.shutdown();
    }
    if (callbackDispatcher != null) {
      callbackDispatcher.shutdown();
    }
    if (taskStore instanceof AutoCloseable ac) {
      ac.close();
    }
    if (dlqStore instanceof AutoCloseable ac) {
      ac.close();
    }
    if (clientStore != null) {
      clientStore.close();
    }
  }

  /**
   * Returns the service responsible for client authentication and registration.
   *
   * @return the {@link AuthService}
   */
  public AuthService getAuthService() {
    return authService;
  }

  /**
   * Returns the manager responsible for client sessions.
   *
   * @return the {@link SessionManager}
   */
  public SessionManager getSessionManager() {
    return sessionManager;
  }

  /**
   * Returns the timer engine responsible for task scheduling.
   *
   * @return the {@link Timer}
   */
  public Timer getTimer() {
    return timer;
  }
}
