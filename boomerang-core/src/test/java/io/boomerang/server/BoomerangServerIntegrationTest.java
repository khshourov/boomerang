package io.boomerang.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.boomerang.BoomerangBootstrap;
import io.boomerang.config.ServerConfig;
import io.boomerang.proto.AuthHandshake;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.Status;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoomerangServerIntegrationTest {
  @TempDir Path tempDir;
  private BoomerangBootstrap bootstrap;
  private ServerConfig config;
  private EventLoopGroup clientGroup;
  private final BlockingQueue<BoomerangEnvelope> responses = new LinkedBlockingQueue<>();

  @BeforeEach
  void setUp() throws IOException {
    // Set valid Base64 encoded 32-byte master key (12345678901234567890123456789012)
    System.setProperty("BOOMERANG_MASTER_KEY", "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=");

    Path configFile = tempDir.resolve("boomerang-server.properties");
    String configContent =
        """
        server.port=9974
        admin.client.id=admin
        admin.password=admin123
        rocksdb.path=%s
        rocksdb.client.path=%s
        rocksdb.dlq.path=%s
        """
            .formatted(
                tempDir.resolve("rocksdb").toString(),
                tempDir.resolve("clients").toString(),
                tempDir.resolve("dlq").toString());
    Files.writeString(configFile, configContent);

    config = new ServerConfig(configFile.toString());
    bootstrap = new BoomerangBootstrap(config);
    bootstrap.start();

    clientGroup = new NioEventLoopGroup();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (clientGroup != null) {
      clientGroup.shutdownGracefully();
    }
    if (bootstrap != null) {
      bootstrap.close();
    }
    System.clearProperty("BOOMERANG_MASTER_KEY");
  }

  @Test
  void testFullFlow() throws Exception {
    Bootstrap b = new Bootstrap();
    b.group(clientGroup)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                ch.pipeline().addLast(new LengthFieldPrepender(4));
                ch.pipeline().addLast(new ProtobufDecoder(BoomerangEnvelope.getDefaultInstance()));
                ch.pipeline().addLast(new ProtobufEncoder());
                ch.pipeline()
                    .addLast(
                        new SimpleChannelInboundHandler<BoomerangEnvelope>() {
                          @Override
                          protected void channelRead0(
                              ChannelHandlerContext ctx, BoomerangEnvelope msg) {
                            responses.add(msg);
                          }
                        });
              }
            });

    Channel channel = b.connect("localhost", 9974).sync().channel();

    // 1. Authenticate
    AuthHandshake handshake =
        AuthHandshake.newBuilder().setClientId("admin").setPassword("admin123").build();
    channel.writeAndFlush(BoomerangEnvelope.newBuilder().setAuthHandshake(handshake).build());

    BoomerangEnvelope authResponse = responses.poll(10, TimeUnit.SECONDS);
    assertThat(authResponse).isNotNull();
    assertThat(authResponse.getAuthResponse().getStatus()).isEqualTo(Status.OK);
    String sessionId = authResponse.getAuthResponse().getSessionId();

    // 2. Register Task
    io.boomerang.proto.Task taskRequest =
        io.boomerang.proto.Task.newBuilder()
            .setPayload(com.google.protobuf.ByteString.copyFromUtf8("integration-test"))
            .setDelayMs(5000)
            .build();
    channel.writeAndFlush(
        BoomerangEnvelope.newBuilder()
            .setSessionId(sessionId)
            .setRegistrationRequest(taskRequest)
            .build());

    BoomerangEnvelope regResponse = responses.poll(10, TimeUnit.SECONDS);
    assertThat(regResponse).isNotNull();
    assertThat(regResponse.getRegistrationResponse().getStatus()).isEqualTo(Status.OK);
    String taskId = regResponse.getRegistrationResponse().getTaskId();
    assertThat(taskId).isNotEmpty();

    // 3. Verify task exists in timer (indirectly)
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              var taskOpt = bootstrap.getTimer().get(taskId);
              assertThat(taskOpt).isPresent();
            });

    channel.close().sync();
  }
}
