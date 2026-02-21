package io.boomerang.server.callback;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import io.boomerang.model.CallbackConfig;
import io.boomerang.timer.TimerTask;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UdpCallbackHandlerTest {

  private EventLoopGroup group;
  private UdpCallbackHandler handler;
  private int port;
  private final AtomicInteger receivedCount = new AtomicInteger(0);

  @BeforeEach
  void setUp() throws InterruptedException {
    group = new NioEventLoopGroup(1);
    Bootstrap b = new Bootstrap();
    b.group(group)
        .channel(NioDatagramChannel.class)
        .handler(
            new ChannelInitializer<NioDatagramChannel>() {
              @Override
              public void initChannel(NioDatagramChannel ch) {
                ch.pipeline().addLast(new MockUdpServerHandler());
              }
            });

    port =
        b.bind(0).sync().channel().localAddress() instanceof InetSocketAddress addr
            ? addr.getPort()
            : 0;

    handler = new UdpCallbackHandler();
  }

  @AfterEach
  void tearDown() {
    group.shutdownGracefully();
    handler.shutdown();
  }

  @Test
  void shouldDeliverSuccessfully() {
    TimerTask task = new TimerTask("task-1", "client-1", 100, "hello".getBytes(), 0, () -> {});
    CallbackConfig config = new CallbackConfig(CallbackConfig.Protocol.UDP, "localhost:" + port);

    assertThatCode(() -> handler.handle(task, config)).doesNotThrowAnyException();

    await().atMost(Duration.ofSeconds(2)).until(() -> receivedCount.get() == 1);
  }

  private class MockUdpServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
      receivedCount.incrementAndGet();
    }
  }
}
