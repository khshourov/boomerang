package io.boomerang.cli.client;

import io.boomerang.proto.BoomerangEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.CompletableFuture;

/**
 * Netty handler that processes responses from the Boomerang server.
 *
 * @since 0.1.0
 */
public class BoomerangClientHandler extends SimpleChannelInboundHandler<BoomerangEnvelope> {
  private final CompletableFuture<BoomerangEnvelope> responseFuture = new CompletableFuture<>();

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, BoomerangEnvelope envelope) {
    responseFuture.complete(envelope);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    responseFuture.completeExceptionally(cause);
    ctx.close();
  }

  /**
   * Returns a future that will be completed with the server's response.
   *
   * @return the {@link CompletableFuture} for the response
   */
  public CompletableFuture<BoomerangEnvelope> getResponseFuture() {
    return responseFuture;
  }
}
