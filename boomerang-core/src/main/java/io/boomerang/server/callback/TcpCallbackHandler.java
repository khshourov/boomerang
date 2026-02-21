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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for raw TCP callbacks using Protobuf.
 *
 * <p>Establishes a connection to the remote endpoint, sends a {@link CallbackRequest} wrapped in a
 * {@link BoomerangEnvelope}, and waits for a {@link CallbackResponse} acknowledgment.
 *
 * @since 1.0.0
 */
public class TcpCallbackHandler implements CallbackHandler {
  private static final Logger log = LoggerFactory.getLogger(TcpCallbackHandler.class);

  private final EventLoopGroup group;
  private final long timeoutMs;

  /**
   * Constructs a new handler with a shared event loop group.
   *
   * @param timeoutMs the maximum time to wait for a connection and response
   */
  public TcpCallbackHandler(long timeoutMs) {
    this.group = new NioEventLoopGroup(1); // Small pool for outbound TCP callbacks
    this.timeoutMs = timeoutMs;
  }

  @Override
  public void handle(TimerTask task, CallbackConfig config) throws CallbackException {
    String[] hostPort = config.endpoint().split(":");
    if (hostPort.length != 2) {
      throw new IllegalArgumentException("Invalid TCP endpoint: " + config.endpoint());
    }
    String host = hostPort[0];
    int port = Integer.parseInt(hostPort[1]);

    CompletableFuture<CallbackResponse> responseFuture = new CompletableFuture<>();

    Bootstrap b = new Bootstrap();
    b.group(group)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                ch.pipeline().addLast(new LengthFieldPrepender(4));
                ch.pipeline().addLast(new ProtobufDecoder(BoomerangEnvelope.getDefaultInstance()));
                ch.pipeline().addLast(new ProtobufEncoder());
                ch.pipeline().addLast(new CallbackClientHandler(responseFuture));
              }
            });

    log.debug("Connecting to TCP callback endpoint {}:{}", host, port);
    try {
      ChannelFuture f = b.connect(host, port).sync();
      Channel channel = f.channel();

      try {
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

        channel.writeAndFlush(envelope);

        CallbackResponse response = responseFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        if (response.getStatus() != Status.OK) {
          throw new CallbackException(
              "TCP callback failed with status "
                  + response.getStatus()
                  + ": "
                  + response.getErrorMessage());
        }
      } finally {
        channel.close().sync();
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
    group.shutdownGracefully();
  }

  private static class CallbackClientHandler
      extends SimpleChannelInboundHandler<BoomerangEnvelope> {
    private final CompletableFuture<CallbackResponse> responseFuture;

    public CallbackClientHandler(CompletableFuture<CallbackResponse> responseFuture) {
      this.responseFuture = responseFuture;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BoomerangEnvelope msg) {
      if (msg.hasCallbackResponse()) {
        responseFuture.complete(msg.getCallbackResponse());
      } else {
        log.warn("Received unexpected message type over TCP callback: {}", msg.getPayloadCase());
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      responseFuture.completeExceptionally(cause);
      ctx.close();
    }
  }
}
