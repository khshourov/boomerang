package io.boomerang.server.callback;

import com.google.protobuf.ByteString;
import io.boomerang.model.CallbackConfig;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.proto.CallbackResponse;
import io.boomerang.proto.Status;
import io.boomerang.timer.TimerTask;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for raw TCP callbacks using Protobuf with connection pooling.
 *
 * <p>Uses a {@link FixedChannelPool} per endpoint to reuse connections and limit concurrency.
 *
 * @since 1.0.0
 */
public class TcpCallbackHandler implements CallbackHandler {
  private static final Logger log = LoggerFactory.getLogger(TcpCallbackHandler.class);
  private static final AttributeKey<CompletableFuture<CallbackResponse>> RESPONSE_FUTURE_KEY =
      AttributeKey.valueOf("responseFuture");

  private final EventLoopGroup group;
  private final long timeoutMs;
  private final AbstractChannelPoolMap<InetSocketAddress, FixedChannelPool> poolMap;

  /**
   * Constructs a new handler with a shared event loop group and connection pooling.
   *
   * @param timeoutMs the maximum time to wait for a connection and response
   * @param maxConnections per endpoint connection limit
   */
  public TcpCallbackHandler(long timeoutMs, int maxConnections) {
    this.group = new NioEventLoopGroup();
    this.timeoutMs = timeoutMs;
    this.poolMap =
        new AbstractChannelPoolMap<>() {
          @Override
          protected FixedChannelPool newPool(InetSocketAddress key) {
            Bootstrap b =
                new Bootstrap().group(group).channel(NioSocketChannel.class).remoteAddress(key);
            return new FixedChannelPool(
                b,
                new AbstractChannelPoolHandler() {
                  @Override
                  public void channelCreated(Channel ch) {
                    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                    ch.pipeline().addLast(new LengthFieldPrepender(4));
                    ch.pipeline()
                        .addLast(new ProtobufDecoder(BoomerangEnvelope.getDefaultInstance()));
                    ch.pipeline().addLast(new ProtobufEncoder());
                    ch.pipeline().addLast(new CallbackClientHandler());
                  }
                },
                maxConnections);
          }
        };
  }

  @Override
  public void handle(TimerTask task, CallbackConfig config) throws CallbackException {
    String[] hostPort = config.endpoint().split(":");
    if (hostPort.length != 2) {
      throw new IllegalArgumentException("Invalid TCP endpoint: " + config.endpoint());
    }
    String host = hostPort[0];
    int port = Integer.parseInt(hostPort[1]);
    InetSocketAddress remoteAddress = new InetSocketAddress(host, port);

    FixedChannelPool pool = poolMap.get(remoteAddress);
    CompletableFuture<CallbackResponse> responseFuture = new CompletableFuture<>();

    Future<Channel> acquireFuture = pool.acquire();
    acquireFuture.addListener(
        (Future<Channel> f) -> {
          if (f.isSuccess()) {
            Channel channel = f.getNow();
            channel.attr(RESPONSE_FUTURE_KEY).set(responseFuture);

            CallbackRequest request =
                CallbackRequest.newBuilder()
                    .setTaskId(task.getTaskId())
                    .setPayload(
                        task.getPayload() != null
                            ? ByteString.copyFrom(task.getPayload())
                            : ByteString.EMPTY)
                    .build();

            BoomerangEnvelope envelope =
                BoomerangEnvelope.newBuilder().setCallbackRequest(request).build();

            channel
                .writeAndFlush(envelope)
                .addListener(
                    writeFuture -> {
                      if (!writeFuture.isSuccess()) {
                        responseFuture.completeExceptionally(writeFuture.cause());
                      }
                    });

            // Ensure channel is released back to pool after response or failure
            responseFuture.whenComplete((res, err) -> pool.release(channel));
          } else {
            responseFuture.completeExceptionally(f.cause());
          }
        });

    try {
      CallbackResponse response = responseFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
      if (response.getStatus() != Status.OK) {
        throw new CallbackException(
            "TCP callback failed with status "
                + response.getStatus()
                + ": "
                + response.getErrorMessage());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CallbackException("TCP callback connection interrupted", e);
    } catch (Exception e) {
      if (e instanceof CallbackException exception) {
        throw exception;
      }
      throw new CallbackException("TCP callback failed: " + e.getMessage(), e);
    }
  }

  @Override
  public CallbackConfig.Protocol getProtocol() {
    return CallbackConfig.Protocol.TCP;
  }

  @Override
  public void shutdown() {
    poolMap.close();
    group.shutdownGracefully();
  }

  private static class CallbackClientHandler
      extends SimpleChannelInboundHandler<BoomerangEnvelope> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BoomerangEnvelope msg) {
      CompletableFuture<CallbackResponse> future = ctx.channel().attr(RESPONSE_FUTURE_KEY).get();
      if (future != null) {
        if (msg.hasCallbackResponse()) {
          future.complete(msg.getCallbackResponse());
        } else {
          log.warn("Received unexpected message type over TCP callback: {}", msg.getPayloadCase());
        }
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      CompletableFuture<CallbackResponse> future = ctx.channel().attr(RESPONSE_FUTURE_KEY).get();
      if (future != null) {
        future.completeExceptionally(cause);
      }
      ctx.close();
    }
  }
}
