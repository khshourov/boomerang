package io.boomerang.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Boomerang CLI tool for task management.
 *
 * @since 0.1.0
 */
@Command(
    name = "boomtool",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "Boomerang CLI tool for task management.")
public class BoomTool implements Runnable {

  /**
   * Main entry point for the CLI.
   *
   * @param args command line arguments.
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new BoomTool()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    System.out.println("Boomerang CLI Tool - v0.1.0");
  }
}
