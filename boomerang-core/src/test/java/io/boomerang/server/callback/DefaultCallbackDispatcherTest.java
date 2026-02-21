package io.boomerang.server.callback;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.boomerang.auth.ClientStore;
import io.boomerang.model.CallbackConfig;
import io.boomerang.model.Client;
import io.boomerang.timer.TimerTask;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultCallbackDispatcherTest {

  @Mock private ClientStore clientStore;
  @Mock private CallbackHandler tcpHandler;
  @Mock private CallbackHandler httpHandler;

  private DefaultCallbackDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    when(tcpHandler.getProtocol()).thenReturn(CallbackConfig.Protocol.TCP);
    when(httpHandler.getProtocol()).thenReturn(CallbackConfig.Protocol.HTTP);
    dispatcher = new DefaultCallbackDispatcher(clientStore, List.of(tcpHandler, httpHandler));
  }

  @Test
  void shouldDispatchToCorrectHandler() throws CallbackException {
    String clientId = "test-client";
    TimerTask task = new TimerTask("task-1", clientId, 100, new byte[0], 0, () -> {});

    CallbackConfig config = new CallbackConfig(CallbackConfig.Protocol.TCP, "localhost:1234");
    Client client = new Client(clientId, "hash", false, config, null, null);

    when(clientStore.findById(clientId)).thenReturn(Optional.of(client));

    dispatcher.dispatch(task);

    verify(tcpHandler).handle(task, config);
    verify(httpHandler, never()).handle(any(), any());
  }

  @Test
  void shouldThrowWhenClientNotFound() {
    String clientId = "unknown";
    TimerTask task = new TimerTask("task-1", clientId, 100, new byte[0], 0, () -> {});

    when(clientStore.findById(clientId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> dispatcher.dispatch(task))
        .isInstanceOf(CallbackException.class)
        .hasMessageContaining("Unknown client");
  }

  @Test
  void shouldThrowWhenCallbackConfigIsNull() {
    String clientId = "no-config-client";
    TimerTask task = new TimerTask("task-1", clientId, 100, new byte[0], 0, () -> {});

    Client client = new Client(clientId, "hash", false, null, null, null);

    when(clientStore.findById(clientId)).thenReturn(Optional.of(client));

    assertThatThrownBy(() -> dispatcher.dispatch(task))
        .isInstanceOf(CallbackException.class)
        .hasMessageContaining("No callback configuration for client");
  }

  @Test
  void shouldThrowWhenProtocolNotSupported() {
    String clientId = "udp-client";
    TimerTask task = new TimerTask("task-1", clientId, 100, new byte[0], 0, () -> {});

    CallbackConfig config = new CallbackConfig(CallbackConfig.Protocol.UDP, "localhost:5678");
    Client client = new Client(clientId, "hash", false, config, null, null);

    when(clientStore.findById(clientId)).thenReturn(Optional.of(client));

    assertThatThrownBy(() -> dispatcher.dispatch(task))
        .isInstanceOf(CallbackException.class)
        .hasMessageContaining("Unsupported protocol: UDP");
  }

  @Test
  void shouldShutdownHandlers() {
    dispatcher.shutdown();
    verify(tcpHandler).shutdown();
    verify(httpHandler).shutdown();
  }
}
