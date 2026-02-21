package io.boomerang.server.callback;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.boomerang.model.CallbackConfig;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.CallbackResponse;
import io.boomerang.proto.Status;
import io.boomerang.timer.TimerTask;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TcpCallbackHandlerTest {

  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private TcpCallbackHandler handler;
  private int port;
  private volatile Status serverResponseStatus = Status.OK;

  @BeforeEach
  void setUp() throws InterruptedException {
    bossGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup(1);

    ServerBootstrap b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              public void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                ch.pipeline().addLast(new LengthFieldPrepender(4));
                ch.pipeline().addLast(new ProtobufDecoder(BoomerangEnvelope.getDefaultInstance()));
                ch.pipeline().addLast(new ProtobufEncoder());
                ch.pipeline().addLast(new MockServerHandler());
              }
            });

    port =
        b.bind(0).sync().channel().localAddress() instanceof InetSocketAddress addr
            ? addr.getPort()
            : 0;

    handler = new TcpCallbackHandler(2000);
  }

  @AfterEach
  void tearDown() {
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
    handler.shutdown();
  }

  @Test
  void shouldDeliverSuccessfully() {
    serverResponseStatus = Status.OK;
    TimerTask task = new TimerTask("task-1", "client-1", 100, "hello".getBytes(), 0, () -> {});
    CallbackConfig config = new CallbackConfig(CallbackConfig.Protocol.TCP, "localhost:" + port);

    assertThatCode(() -> handler.handle(task, config)).doesNotThrowAnyException();
  }

  @Test
  void shouldThrowWhenStatusIsNotOk() {
    serverResponseStatus = Status.ERROR;
    TimerTask task = new TimerTask("task-1", "client-1", 100, "hello".getBytes(), 0, () -> {});
    CallbackConfig config = new CallbackConfig(CallbackConfig.Protocol.TCP, "localhost:" + port);

    assertThatThrownBy(() -> handler.handle(task, config))
        .isInstanceOf(CallbackException.class)
        .hasMessageContaining("ERROR");
  }

  private class MockServerHandler extends SimpleChannelInboundHandler<BoomerangEnvelope> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BoomerangEnvelope msg) {
      if (msg.hasCallbackRequest()) {
        CallbackResponse response =
            CallbackResponse.newBuilder()
                .setStatus(serverResponseStatus)
                .setErrorMessage(serverResponseStatus == Status.OK ? "" : "Manual error")
                .build();
        BoomerangEnvelope envelope =
            BoomerangEnvelope.newBuilder().setCallbackResponse(response).build();
        ctx.writeAndFlush(envelope);
      }
    }
  }
}
