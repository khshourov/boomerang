package io.boomerang.cli;

import io.boomerang.cli.client.BoomerangClient;
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
      required = true,
      scope = ScopeType.INHERIT)
  String clientId;

  @Option(
      names = {"-p", "--password"},
      description = "Client password",
      required = true,
      interactive = true,
      arity = "0..1",
      scope = ScopeType.INHERIT)
  char[] password;

  // Persistent client for interactive mode
  private BoomerangClient interactiveClient;

  /**
   * Main entry point for the CLI.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    CommandLine cmd = new CommandLine(new BoomTool());
    int exitCode = cmd.execute(args);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    // This is called when no subcommand is provided (Interactive Mode)
    System.out.println("Entering interactive mode. Type 'exit' to quit.");

    try (BoomerangClient client = new BoomerangClient(host, port)) {
      client.connect();
      if (!client.login(clientId, password)) {
        System.err.println("Error: Authentication failed.");
        return;
      }
      this.interactiveClient = client;

      // Set up JLine terminal and reader
      Terminal terminal = TerminalBuilder.builder().build();
      PrintWriter out = terminal.writer();

      // Use 'this' BoomTool instance as the root for the interactive shell
      // and create a factory that provides 'this' BoomTool instance to subcommands
      IFactory factory =
          new IFactory() {
            @Override
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

      // One-shot mode: create, connect, login, execute, and close
      try (BoomerangClient client = createClient(root.host, root.port)) {
        client.connect();
        if (!client.login(root.clientId, root.password)) {
          System.err.println("Error: Authentication failed.");
          return 1;
        }
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
     * Creates a new client instance. Can be overridden for testing.
     *
     * @param host server host
     * @param port server port
     * @return a new {@link BoomerangClient}
     */
    protected BoomerangClient createClient(String host, int port) {
      return new BoomerangClient(host, port);
    }
  }
}
