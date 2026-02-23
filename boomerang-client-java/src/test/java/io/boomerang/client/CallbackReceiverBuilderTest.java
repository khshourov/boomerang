package io.boomerang.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class CallbackReceiverBuilderTest {

  @Test
  void testBuildTcp() {
    CallbackHandler handler = mock(CallbackHandler.class);
    CallbackReceiver receiver =
        new CallbackReceiverBuilder().withPort(12350).withHandler(handler).buildTcp();
    assertInstanceOf(TcpCallbackReceiver.class, receiver);
  }

  @Test
  void testBuildUdp() {
    CallbackHandler handler = mock(CallbackHandler.class);
    CallbackReceiver receiver =
        new CallbackReceiverBuilder().withPort(12351).withHandler(handler).buildUdp();
    assertInstanceOf(UdpCallbackReceiver.class, receiver);
  }

  @Test
  void testBuildHttp() {
    CallbackHandler handler = mock(CallbackHandler.class);
    CallbackReceiver receiver =
        new CallbackReceiverBuilder().withPort(12352).withHandler(handler).buildHttp();
    assertInstanceOf(HttpCallbackReceiver.class, receiver);
  }

  @Test
  void testBuildGrpc() {
    CallbackHandler handler = mock(CallbackHandler.class);
    CallbackReceiver receiver =
        new CallbackReceiverBuilder().withPort(12353).withHandler(handler).buildGrpc();
    assertInstanceOf(GrpcCallbackReceiver.class, receiver);
  }

  @Test
  void testBuildFailsWithoutHandler() {
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class,
        () -> {
          new CallbackReceiverBuilder().withPort(80).buildTcp();
        });
  }

  @Test
  void testBuildFailsWithInvalidPort() {
    CallbackHandler handler = mock(CallbackHandler.class);
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> {
          new CallbackReceiverBuilder().withPort(-1).withHandler(handler).buildTcp();
        });
  }
}
