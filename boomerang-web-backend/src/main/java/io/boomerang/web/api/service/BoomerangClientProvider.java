package io.boomerang.web.api.service;

import io.boomerang.client.BoomerangClient;
import io.boomerang.client.DefaultBoomerangClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Component for providing {@link BoomerangClient} instances.
 *
 * <p>This provider handles the instantiation of clients with the configured server host and port.
 */
@Component
public class BoomerangClientProvider {

  @Value("${boomerang.server.host}")
  private String host;

  @Value("${boomerang.server.port}")
  private int port;

  /**
   * Creates a new unauthenticated Boomerang client.
   *
   * @return a new {@link BoomerangClient} instance
   */
  public BoomerangClient createClient() {
    return new DefaultBoomerangClient(host, port);
  }

  /**
   * Creates a new Boomerang client with an existing session.
   *
   * @param sessionId the session ID to use
   * @return a new authenticated {@link BoomerangClient} instance
   */
  public BoomerangClient createClient(String sessionId) {
    return new DefaultBoomerangClient(host, port, sessionId);
  }
}
