package io.boomerang.server.callback;

import com.google.protobuf.ByteString;
import io.boomerang.model.CallbackConfig;
import io.boomerang.proto.CallbackRequest;
import io.boomerang.timer.TimerTask;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for UDP callbacks.
 *
 * <p>Delivers task payloads as fire-and-forget {@link CallbackRequest} messages over UDP.
 *
 * @since 1.0.0
 */
public class UdpCallbackHandler implements CallbackHandler {
  private static final Logger log = LoggerFactory.getLogger(UdpCallbackHandler.class);

  private final EventLoopGroup group;
  private final Channel channel;

  /** Constructs a new handler and initializes the UDP channel. */
  public UdpCallbackHandler() {
    this.group = new NioEventLoopGroup(1);
    Bootstrap b = new Bootstrap();
    b.group(group)
        .channel(NioDatagramChannel.class)
        .handler(new io.netty.channel.ChannelInboundHandlerAdapter());
    try {
      this.channel = b.bind(0).sync().channel();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to initialize UDP channel", e);
    }
  }

  @Override
  public void handle(TimerTask task, CallbackConfig config) throws CallbackException {
    String[] hostPort = config.endpoint().split(":");
    if (hostPort.length != 2) {
      throw new CallbackException("Invalid UDP endpoint: " + config.endpoint());
    }
    String host = hostPort[0];
    int port = Integer.parseInt(hostPort[1]);

    CallbackRequest request =
        CallbackRequest.newBuilder()
            .setTaskId(task.getTaskId())
            .setPayload(
                task.getPayload() != null
                    ? ByteString.copyFrom(task.getPayload())
                    : ByteString.EMPTY)
            .build();

    byte[] bytes = request.toByteArray();
    log.debug("Sending UDP datagram to {}:{}, size: {}", host, port, bytes.length);

    channel.writeAndFlush(
        new DatagramPacket(Unpooled.copiedBuffer(bytes), new InetSocketAddress(host, port)));
  }

  @Override
  public CallbackConfig.Protocol getProtocol() {
    return CallbackConfig.Protocol.UDP;
  }

  @Override
  public void shutdown() {
    if (channel != null) {
      channel.close();
    }
    group.shutdownGracefully();
  }
}
