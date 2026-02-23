package io.boomerang.cli;

import io.boomerang.client.BoomerangClient;
import io.boomerang.client.DefaultBoomerangClient;
import io.boomerang.client.SmartBoomerangClient;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;
import picocli.shell.jline3.PicocliCommands;

/**
 * Main command for the Boomerang CLI tool.
 *
 * @since 0.1.0
 */
@Command(
    name = "boomtool",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "Boomerang CLI tool for task and client management.",
    subcommands = {BoomTool.TaskCommand.class, BoomTool.AdminCommand.class})
public class BoomTool implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(BoomTool.class);

  @Option(
      names = {"-H", "--host"},
      defaultValue = "localhost",
      description = "Server host (default: ${DEFAULT-VALUE})",
      scope = ScopeType.INHERIT)
  String host;

  @Option(
      names = {"-P", "--port"},
      defaultValue = "9973",
      description = "Server port (default: ${DEFAULT-VALUE})",
      scope = ScopeType.INHERIT)
  int port;

  @Option(
      names = {"-u", "--user"},
      description = "Client identifier",
      scope = ScopeType.INHERIT)
  String clientId;

  @Option(
      names = {"-p", "--password"},
      description = "Client password",
      interactive = true,
      arity = "0..1",
      scope = ScopeType.INHERIT)
  char[] password;

  // Persistent client for interactive mode
  private BoomerangClient interactiveClient;

  /**
   * Creates a new client instance. Can be overridden for testing.
   *
   * @param host server host
   * @param port server port
   * @param clientId client identifier
   * @param password client password
   * @return a new {@link BoomerangClient}
   */
  protected BoomerangClient createClient(String host, int port, String clientId, String password) {
    return new SmartBoomerangClient(new DefaultBoomerangClient(host, port), clientId, password);
  }

  /**
   * Main entry point for the CLI.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    CommandLine cmd = new CommandLine(new BoomTool());
    cmd.setAllowSubcommandsAsOptionParameters(true);
    int exitCode = cmd.execute(args);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    // This is called when no subcommand is provided (Interactive Mode)
    if (clientId == null || password == null) {
      System.err.println(
          "Error: Missing required options: '--user' and '--password' are required for interactive mode.");
      CommandLine.usage(this, System.err);
      return;
    }

    System.out.println("Entering interactive mode. Type 'exit' to quit.");

    try (BoomerangClient client = createClient(host, port, clientId, new String(password))) {
      client.connect(); // SmartBoomerangClient handles login during connect
      this.interactiveClient = client;

      // Set up JLine terminal and reader
      Terminal terminal = TerminalBuilder.builder().build();
      PrintWriter out = terminal.writer();

      // Use 'this' BoomTool instance as the root for the interactive shell
      // and create a factory that provides 'this' BoomTool instance to subcommands
      IFactory factory =
          new IFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <K> K create(Class<K> cls) throws Exception {
              if (cls == BoomTool.class) {
                return (K) BoomTool.this;
              }
              return CommandLine.defaultFactory().create(cls);
            }
          };

      CommandLine shellCmd = new CommandLine(this, factory);
      shellCmd.addSubcommand("exit", new ExitCommand());
      PicocliCommands picocliCommands = new PicocliCommands(shellCmd);
      LineReader reader =
          LineReaderBuilder.builder()
              .terminal(terminal)
              .completer(picocliCommands.compileCompleters())
              .build();

      String prompt = "> ";
      String line;
      while (true) {
        try {
          line = reader.readLine(prompt);
          if (line == null || line.equalsIgnoreCase("exit")) {
            break;
          }
          if (line.trim().isEmpty()) {
            continue;
          }
          shellCmd.execute(line.split("\\s+"));
        } catch (UserInterruptException e) {
          // Ignore
        } catch (EndOfFileException e) {
          break;
        } catch (Exception e) {
          out.println("Error: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      System.err.println("Critical Error: " + e.getMessage());
    } finally {
      if (password != null) {
        java.util.Arrays.fill(password, '\0');
      }
    }
  }

  /** Subcommand for task management. */
  @Command(
      name = "task",
      description = "Manage tasks.",
      subcommands = {
        TaskRegisterCommand.class,
        TaskCancelCommand.class,
        TaskListCommand.class,
        TaskGetCommand.class
      })
  static class TaskCommand implements Runnable {
    @Override
    public void run() {
      CommandLine.usage(this, System.out);
    }
  }

  /** Subcommand for administrative management. */
  @Command(
      name = "admin",
      description = "Administrative commands.",
      subcommands = {AdminClientRegisterCommand.class, AdminClientDeregisterCommand.class})
  static class AdminCommand implements Runnable {
    @Override
    public void run() {
      CommandLine.usage(this, System.out);
    }
  }

  /** Exit command for interactive mode. */
  @Command(name = "exit", description = "Exit the interactive shell.")
  static class ExitCommand implements Runnable {
    @Override
    public void run() {
      // Just a marker command, the loop handles the actual exit
    }
  }

  /** Base class for commands that need a BoomerangClient. */
  @Command
  abstract static class BaseCommand implements Callable<Integer> {
    @Spec CommandSpec spec;

    /**
     * Executes the command logic.
     *
     * @param client the authenticated {@link BoomerangClient}
     * @return exit code
     * @throws Exception if an error occurs during execution
     */
    protected abstract Integer executeWithClient(BoomerangClient client) throws Exception;

    @Override
    public Integer call() {
      // Set system property to configure slf4j-simple
      System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
      System.setProperty("org.slf4j.simpleLogger.showDateTime", "false");
      System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
      System.setProperty("org.slf4j.simpleLogger.showLogName", "false");

      // Attempt to find BoomTool in the command hierarchy
      BoomTool root = findRoot(spec);

      if (root == null) {
        System.err.println("Error: Could not find root command context.");
        return 1;
      }

      // If we are in interactive mode, use the persistent client
      if (root.interactiveClient != null) {
        try {
          return executeWithClient(root.interactiveClient);
        } catch (Exception e) {
          System.err.println("Error: " + e.getMessage());
          return 1;
        }
      }

      // One-shot mode: validate options, create, connect, login, execute, and close
      if (root.clientId == null || root.password == null) {
        System.err.println(
            "Error: Missing required options: '--user' and '--password' are required.");
        CommandLine.usage(root, System.err);
        return 1;
      }

      try (BoomerangClient client =
          root.createClient(root.host, root.port, root.clientId, new String(root.password))) {
        client.connect(); // SmartBoomerangClient handles login
        return executeWithClient(client);
      } catch (Exception e) {
        System.err.println("Error: " + e.getMessage());
        return 1;
      } finally {
        if (root.interactiveClient == null && root.password != null) {
          java.util.Arrays.fill(root.password, '\0');
        }
      }
    }

    private BoomTool findRoot(CommandSpec spec) {
      CommandSpec current = spec;
      while (current != null) {
        Object userObject = current.userObject();
        if (userObject instanceof BoomTool) {
          return (BoomTool) userObject;
        }
        current = current.parent();
      }
      return null;
    }

    /**
     * Parses a compact interval string (e.g., "5m", "1h", "10s") into milliseconds.
     *
     * @param interval the interval string to parse
     * @return the total duration in milliseconds
     * @throws IllegalArgumentException if the format is invalid
     */
    protected long parseIntervalToMs(String interval) {
      java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)([smhd])");
      java.util.regex.Matcher matcher = pattern.matcher(interval.toLowerCase());
      long totalMs = 0;
      boolean found = false;
      while (matcher.find()) {
        found = true;
        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        switch (unit) {
          case "s" -> totalMs += value * 1000;
          case "m" -> totalMs += value * 60 * 1000;
          case "h" -> totalMs += value * 60 * 60 * 1000;
          case "d" -> totalMs += value * 24 * 60 * 60 * 1000;
        }
      }
      if (!found) {
        try {
          return Long.parseLong(interval);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Invalid interval format: " + interval);
        }
      }
      return totalMs;
    }
  }
}
