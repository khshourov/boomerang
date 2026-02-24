package io.boomerang.web.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter for authenticating requests using the {@code X-Boomerang-Session-Id} header.
 *
 * <p>If the header is present, it creates an authentication token and sets it in the {@link
 * SecurityContextHolder}. The core server will validate the session ID during actual operations.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

  private static final String SESSION_HEADER = "X-Boomerang-Session-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String sessionId = request.getHeader(SESSION_HEADER);

    if (sessionId != null && !sessionId.isEmpty()) {
      // In a more robust implementation, we might validate the session ID here
      // via a cache or by calling the core server. For now, we trust the header
      // and let the TaskService handle any failures if the session is invalid.
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(sessionId, null, Collections.emptyList());
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    filterChain.doFilter(request, response);
  }
}
