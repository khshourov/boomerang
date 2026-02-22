package io.boomerang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.boomerang.cli.client.BoomerangClient;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CancellationResponse;
import io.boomerang.proto.Status;
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
    cmd =
        new TaskCancelCommand() {
          @Override
          protected BoomerangClient createClient(String host, int port) {
            return mockClient;
          }
        };
    root = new BoomTool();
  }

  @Test
  void shouldCancelTaskSuccessfully() throws Exception {
    // Arrange
    when(mockClient.login(any(), any())).thenReturn(true);
    CancellationResponse response = CancellationResponse.newBuilder().setStatus(Status.OK).build();
    when(mockClient.sendRequest(any(BoomerangEnvelope.class)))
        .thenReturn(BoomerangEnvelope.newBuilder().setCancellationResponse(response).build());

    IFactory factory =
        new IFactory() {
          @Override
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
    verify(mockClient).sendRequest(any(BoomerangEnvelope.class));
  }
}
