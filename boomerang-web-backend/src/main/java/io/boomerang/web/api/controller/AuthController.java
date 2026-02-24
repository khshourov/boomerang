package io.boomerang.web.api.controller;

import io.boomerang.web.api.dto.LoginRequest;
import io.boomerang.web.api.dto.LoginResponse;
import io.boomerang.web.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handling authentication-related requests.
 *
 * <p>This controller provides an endpoint for client login and session creation.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

  @Autowired private AuthService authService;

  /**
   * Endpoint for client login.
   *
   * @param request the {@link LoginRequest} containing client credentials
   * @return a {@link ResponseEntity} containing the {@link LoginResponse} with a session ID
   */
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(response);
  }
}
