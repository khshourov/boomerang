package io.boomerang.web.api.service;

import io.boomerang.client.BoomerangClient;
import io.boomerang.client.BoomerangException;
import io.boomerang.web.api.dto.LoginRequest;
import io.boomerang.web.api.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for handling client authentication.
 *
 * <p>This service communicates with the Boomerang core server to validate credentials and create
 * sessions.
 */
@Service
public class AuthService {

  @Autowired private BoomerangClientProvider clientProvider;

  /**
   * Authenticates a client and returns a session ID.
   *
   * @param request the {@link LoginRequest} containing client credentials
   * @return the {@link LoginResponse} with a session ID
   * @throws BoomerangException if authentication fails
   */
  public LoginResponse login(LoginRequest request) {
    try (BoomerangClient client = clientProvider.createClient()) {
      client.connect();
      client.login(request.clientId(), request.password());
      return new LoginResponse(client.getSessionId());
    } catch (Exception e) {
      if (e instanceof BoomerangException) {
        throw (BoomerangException) e;
      }
      throw new BoomerangException("Login failed", e);
    }
  }
}
