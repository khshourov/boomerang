package io.boomerang.web.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.boomerang.client.BoomerangClient;
import io.boomerang.client.BoomerangException;
import io.boomerang.web.api.dto.LoginRequest;
import io.boomerang.web.api.dto.LoginResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private BoomerangClientProvider clientProvider;
  @Mock private BoomerangClient client;

  @InjectMocks private AuthService authService;

  @Test
  void testLoginSuccess() {
    LoginRequest request = new LoginRequest("user", "pass");
    when(clientProvider.createClient()).thenReturn(client);
    when(client.getSessionId()).thenReturn("session-123");

    LoginResponse response = authService.login(request);

    assertThat(response.sessionId()).isEqualTo("session-123");
    assertThat(response.clientId()).isEqualTo("user");
    verify(client).connect();
    verify(client).login("user", "pass");
  }

  @Test
  void testLoginFailure() {
    LoginRequest request = new LoginRequest("user", "wrong");
    when(clientProvider.createClient()).thenReturn(client);
    doThrow(new RuntimeException("Connection error")).when(client).connect();

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BoomerangException.class)
        .hasMessageContaining("Login failed");
  }
}
