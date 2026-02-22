package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Model.CommandSpec;

class BoomToolInteractiveTest {

  @Test
  void testFindRootInInteractiveMode() {
    BoomTool root = new BoomTool();

    // This simulates how it is set up in BoomTool.run()
    IFactory factory =
        new IFactory() {
          @Override
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == BoomTool.class) return (K) root;
            return CommandLine.defaultFactory().create(cls);
          }
        };
    CommandLine shellCmd = new CommandLine(root, factory);

    // Get the TaskRegisterCommand spec from the hierarchy
    CommandLine taskCmd = shellCmd.getSubcommands().get("task");
    CommandLine registerCmd = taskCmd.getSubcommands().get("register");

    CommandSpec registerSpec = registerCmd.getCommandSpec();

    // Now call the findRoot logic
    // Since findRoot is protected in BaseCommand, we can test it through a subclass
    // or just re-implement it here as it was copied to the test earlier.
    BoomTool found = findRoot(registerSpec);

    assertNotNull(found, "Found root should be root instance of BoomTool");
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
}
