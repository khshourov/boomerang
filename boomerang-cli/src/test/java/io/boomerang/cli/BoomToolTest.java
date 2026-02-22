package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class BoomToolTest {

  @Test
  void shouldRunSmokeTest() {
    BoomTool tool = new BoomTool();
    // Smoke test just checking help/usage doesn't crash
    assertDoesNotThrow(() -> new CommandLine(tool).execute("--help"));
  }
}
