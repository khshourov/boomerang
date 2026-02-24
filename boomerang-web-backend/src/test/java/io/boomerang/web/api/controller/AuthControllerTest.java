package io.boomerang.web.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.boomerang.client.BoomerangException;
import io.boomerang.web.api.dto.LoginRequest;
import io.boomerang.web.api.dto.LoginResponse;
import io.boomerang.web.api.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;

  @Test
  void testLoginSuccess() throws Exception {
    when(authService.login(any(LoginRequest.class)))
        .thenReturn(new LoginResponse("test-session-id"));

    mockMvc
        .perform(
            post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"user\", \"password\":\"pass\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("test-session-id"));
  }

  @Test
  void testLoginFailure() throws Exception {
    when(authService.login(any(LoginRequest.class)))
        .thenThrow(new BoomerangException("Auth failed"));

    mockMvc
        .perform(
            post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"user\", \"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void testLoginValidationError() throws Exception {
    mockMvc
        .perform(
            post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"\", \"password\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
