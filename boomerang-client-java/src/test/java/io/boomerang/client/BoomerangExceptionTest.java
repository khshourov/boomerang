package io.boomerang.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BoomerangExceptionTest {

  @Test
  void testConstructorWithMessage() {
    BoomerangException ex = new BoomerangException("test message");
    assertEquals("test message", ex.getMessage());
  }

  @Test
  void testConstructorWithMessageAndCause() {
    Throwable cause = new RuntimeException("cause");
    BoomerangException ex = new BoomerangException("test message", cause);
    assertEquals("test message", ex.getMessage());
    assertEquals(cause, ex.getCause());
  }
}
