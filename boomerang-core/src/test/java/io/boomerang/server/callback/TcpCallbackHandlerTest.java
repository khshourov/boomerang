package io.boomerang.server.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TcpCallbackHandlerTest {

  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private TcpCallbackHandler handler;
  private int port;
  private volatile Status serverResponseStatus = Status.OK;
  private final AtomicInteger connectionCount = new AtomicInteger(0);
  private final AtomicInteger activeConnections = new AtomicInteger(0);
  private final AtomicInteger maxConcurrentConnections = new AtomicInteger(0);
  private volatile long serverDelayMs = 0;

  @BeforeEach
  void setUp() throws InterruptedException {
    bossGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup(1);
    connectionCount.set(0);
    activeConnections.set(0);
    maxConcurrentConnections.set(0);
    serverDelayMs = 0;

    ServerBootstrap b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              public void initChannel(SocketChannel ch) {
                connectionCount.incrementAndGet();
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

    handler = new TcpCallbackHandler(5000, 2); // Small max connections for testing
  }

  @AfterEach
  void tearDown() {
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
    if (handler != null) {
      handler.shutdown();
    }
  }

  @Test
  void shouldDeliverSuccessfully() {
    serverResponseStatus = Status.OK;
    TimerTask task = new TimerTask("task-1", "client-1", 100, "hello".getBytes(), 0, () -> {});
    CallbackConfig config = new CallbackConfig(CallbackConfig.Protocol.TCP, "localhost:" + port);

    assertThatCode(() -> handler.handle(task, config)).doesNotThrowAnyException();
  }

  @Test
  void shouldReuseConnection() throws CallbackException {
    serverResponseStatus = Status.OK;
    TimerTask task1 = new TimerTask("task-1", "client-1", 100, "hello".getBytes(), 0, () -> {});
    TimerTask task2 = new TimerTask("task-2", "client-1", 100, "world".getBytes(), 0, () -> {});
    CallbackConfig config = new CallbackConfig(CallbackConfig.Protocol.TCP, "localhost:" + port);

    handler.handle(task1, config);
    handler.handle(task2, config);

    assertThat(connectionCount.get()).isEqualTo(1);
  }

  @Test
  void shouldRespectMaxConnections() throws Exception {
    serverResponseStatus = Status.OK;
    serverDelayMs = 1000; // Delay response to keep connections active
    CallbackConfig config = new CallbackConfig(CallbackConfig.Protocol.TCP, "localhost:" + port);

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final int id = i;
      futures.add(
          CompletableFuture.runAsync(
              () -> {
                try {
                  TimerTask task =
                      new TimerTask("task-" + id, "client-1", 100, "data".getBytes(), 0, () -> {});
                  handler.handle(task, config);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));
    }

    // Wait for connections to be established and verify limits
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              assertThat(connectionCount.get()).isEqualTo(2);
              assertThat(maxConcurrentConnections.get()).isLessThanOrEqualTo(2);
            });

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
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
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      int current = activeConnections.incrementAndGet();
      maxConcurrentConnections.accumulateAndGet(current, Math::max);
      super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      activeConnections.decrementAndGet();
      super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BoomerangEnvelope msg) {
      if (msg.hasCallbackRequest()) {
        if (serverDelayMs > 0) {
          ctx.executor().schedule(() -> sendResponse(ctx), serverDelayMs, TimeUnit.MILLISECONDS);
        } else {
          sendResponse(ctx);
        }
      }
    }

    private void sendResponse(ChannelHandlerContext ctx) {
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
