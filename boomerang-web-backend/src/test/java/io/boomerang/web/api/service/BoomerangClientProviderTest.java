package io.boomerang.web.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.boomerang.client.BoomerangClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BoomerangClientProviderTest {

  @Autowired private BoomerangClientProvider provider;

  @Test
  void testCreateClient() {
    BoomerangClient client = provider.createClient();
    assertThat(client).isNotNull();
  }

  @Test
  void testCreateClientWithSession() {
    BoomerangClient client = provider.createClient("session-123");
    assertThat(client).isNotNull();
    assertThat(client.getSessionId()).isEqualTo("session-123");
  }
}
