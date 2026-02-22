package io.boomerang.cli.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.boomerang.proto.BoomerangEnvelope;
import io.netty.channel.ChannelHandlerContext;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoomerangClientHandlerTest {
  private BoomerangClientHandler handler;

  @BeforeEach
  void setUp() {
    handler = new BoomerangClientHandler();
  }

  @Test
  void shouldCompleteFutureOnRead() throws Exception {
    BoomerangEnvelope envelope = BoomerangEnvelope.newBuilder().setSessionId("test").build();
    handler.channelRead0(null, envelope);

    assertTrue(handler.getResponseFuture().isDone());
    assertEquals("test", handler.getResponseFuture().get().getSessionId());
  }

  @Test
  void shouldCompleteExceptionallyOnError() {
    Exception cause = new RuntimeException("error");
    ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    handler.exceptionCaught(ctx, cause);

    assertTrue(handler.getResponseFuture().isCompletedExceptionally());
    assertThrows(ExecutionException.class, () -> handler.getResponseFuture().get());
  }
}
