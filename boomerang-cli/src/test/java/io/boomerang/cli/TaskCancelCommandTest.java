package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.boomerang.client.BoomerangClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

class TaskCancelCommandTest {
  private BoomerangClient mockClient;
  private TaskCancelCommand cmd;
  private BoomTool root;

  @BeforeEach
  void setUp() {
    mockClient = mock(BoomerangClient.class);
    root =
        new BoomTool() {
          @Override
          protected BoomerangClient createClient(
              String host, int port, String clientId, String password) {
            return mockClient;
          }
        };
    cmd = new TaskCancelCommand();
  }

  @Test
  void shouldCancelTaskSuccessfully() throws Exception {
    // Arrange
    doAnswer(
            invocation -> {
              mockClient.login(any(), any());
              return null;
            })
        .when(mockClient)
        .connect();
    when(mockClient.cancel(any())).thenReturn(true);

    IFactory factory =
        new IFactory() {
          @Override
          @SuppressWarnings("unchecked")
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == TaskCancelCommand.class) {
              return (K) cmd;
            }
            return CommandLine.defaultFactory().create(cls);
          }
        };

    // Act
    int exitCode =
        new CommandLine(root, factory)
            .execute("-u", "user", "-p", "pass", "task", "cancel", "-t", "test-task-id");

    // Assert
    assertEquals(0, exitCode);
    verify(mockClient).connect();
    verify(mockClient).login(any(), any());
    verify(mockClient).cancel(any());
  }
}
