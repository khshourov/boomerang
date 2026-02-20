package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class BoomToolTest {

  @Test
  void shouldRunSmokeTest() {
    BoomTool tool = new BoomTool();
    assertDoesNotThrow(tool::run);
  }
}
