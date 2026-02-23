package io.boomerang.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoomerangExceptionTest {

  @Test
  void testConstructorWithMessage() {
    BoomerangException ex = new BoomerangException("test message");
    assertEquals("test message", ex.getMessage());
  }

  @Test
  void testConstructorWithStatus() {
    BoomerangException ex =
        new BoomerangException(io.boomerang.proto.Status.SESSION_EXPIRED, "expired");
    assertEquals("expired", ex.getMessage());
    assertTrue(ex.getStatus().isPresent());
    assertEquals(io.boomerang.proto.Status.SESSION_EXPIRED, ex.getStatus().get());
  }

  @Test
  void testNoStatus() {
    BoomerangException ex = new BoomerangException("no status");
    assertFalse(ex.getStatus().isPresent());
  }
}
