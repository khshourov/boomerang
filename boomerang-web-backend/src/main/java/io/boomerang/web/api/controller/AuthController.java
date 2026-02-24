package io.boomerang.web.api.controller;

import io.boomerang.client.BoomerangException;
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

@RestController
@RequestMapping("/api")
public class AuthController {

  @Autowired private AuthService authService;

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    try {
      LoginResponse response = authService.login(request);
      return ResponseEntity.ok(response);
    } catch (BoomerangException e) {
      return ResponseEntity.status(401).build();
    }
  }
}
