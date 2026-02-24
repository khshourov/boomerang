package io.boomerang.web.api.service;

import io.boomerang.client.BoomerangClient;
import io.boomerang.client.DefaultBoomerangClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BoomerangClientProvider {

  @Value("${boomerang.server.host}")
  private String host;

  @Value("${boomerang.server.port}")
  private int port;

  public BoomerangClient createClient() {
    return new DefaultBoomerangClient(host, port);
  }

  public BoomerangClient createClient(String sessionId) {
    return new DefaultBoomerangClient(host, port, sessionId);
  }
}
