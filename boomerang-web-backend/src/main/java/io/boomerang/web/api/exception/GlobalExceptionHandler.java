package io.boomerang.web.api.exception;

import io.boomerang.client.BoomerangException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the Boomerang Web API.
 *
 * <p>This component catches exceptions thrown by controllers and maps them to appropriate HTTP
 * responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Handles validation errors.
   *
   * @param e the exception to handle
   * @return a {@link ResponseEntity} with validation error details
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationException(
      MethodArgumentNotValidException e) {
    String errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", errors));
  }

  /**
   * Handles {@link BoomerangException} and returns an appropriate error response.
   *
   * @param e the exception to handle
   * @return a {@link ResponseEntity} with error details
   */
  @ExceptionHandler(BoomerangException.class)
  public ResponseEntity<Map<String, String>> handleBoomerangException(BoomerangException e) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    String message = e.getMessage();

    if (message != null) {
      if (message.contains("Login failed") || message.contains("Session ID is required")) {
        status = HttpStatus.UNAUTHORIZED;
      } else if (message.contains("not found")) {
        status = HttpStatus.NOT_FOUND;
      } else if (message.contains("Bad request") || message.contains("mandatory")) {
        status = HttpStatus.BAD_REQUEST;
      }
    }

    return ResponseEntity.status(status)
        .body(Map.of("error", message != null ? message : "An internal error occurred"));
  }

  /**
   * Handles general exceptions and returns a 500 Internal Server Error.
   *
   * @param e the exception to handle
   * @return a {@link ResponseEntity} with error details
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
  }
}
