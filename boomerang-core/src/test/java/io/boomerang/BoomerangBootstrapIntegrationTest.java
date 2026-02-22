package io.boomerang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.protobuf.ByteString;
import com.sun.net.httpserver.HttpServer;
import io.boomerang.auth.AuthService;
import io.boomerang.config.ServerConfig;
import io.boomerang.model.CallbackConfig;
import io.boomerang.proto.AuthHandshake;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.GetTaskRequest;
import io.boomerang.proto.ListTasksRequest;
import io.boomerang.proto.Status;
import io.boomerang.proto.Task;
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
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoomerangBootstrapIntegrationTest {
  @TempDir Path tempDir;
  private BoomerangBootstrap bootstrap;
  private ServerConfig serverConfig;
  private HttpServer mockServer;
  private int mockServerPort;
  private EventLoopGroup clientGroup;
  private final BlockingQueue<BoomerangEnvelope> responses = new LinkedBlockingQueue<>();
  private final AtomicReference<byte[]> receivedPayload = new AtomicReference<>();
  private final AtomicReference<String> receivedTaskId = new AtomicReference<>();

  private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

  @BeforeEach
  void setUp() throws IOException {
    // Start mock HTTP server for callbacks
    mockServer = HttpServer.create(new InetSocketAddress(0), 0);
    mockServer.createContext(
        "/callback",
        exchange -> {
          byte[] body = exchange.getRequestBody().readAllBytes();
          receivedPayload.set(body);
          receivedTaskId.set(exchange.getRequestHeaders().getFirst("X-Boomerang-Task-Id"));
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    mockServer.start();
    mockServerPort = mockServer.getAddress().getPort();

    // Configure and start Boomerang Bootstrap
    System.setProperty("BOOMERANG_MASTER_KEY", MASTER_KEY);
    System.setProperty("rocksdb.client.path", tempDir.resolve("clients").toString());
    System.setProperty("rocksdb.path", tempDir.resolve("rocksdb").toString());
    System.setProperty("rocksdb.dlq.path", tempDir.resolve("dlq").toString());
    System.setProperty("timer.imminent.window.ms", "2000"); // 2s window
    System.setProperty("timer.advance.clock.interval.ms", "50");
    System.setProperty("server.port", "9975");

    serverConfig = new ServerConfig(null);
    bootstrap = new BoomerangBootstrap(serverConfig);
    bootstrap.start();

    // Setup a test client with HTTP callback policy
    AuthService authService = bootstrap.getAuthService();
    authService.registerClient(
        "test-client",
        "password",
        false,
        new CallbackConfig(
            CallbackConfig.Protocol.HTTP, "http://localhost:" + mockServerPort + "/callback"),
        null,
        null);

    authService.registerClient("admin-client", "admin-pass", true, null, null, null);

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
    if (mockServer != null) {
      mockServer.stop(0);
    }
    System.clearProperty("BOOMERANG_MASTER_KEY");
    System.clearProperty("rocksdb.client.path");
    System.clearProperty("rocksdb.path");
    System.clearProperty("rocksdb.dlq.path");
    System.clearProperty("timer.imminent.window.ms");
    System.clearProperty("timer.advance.clock.interval.ms");
    System.clearProperty("server.port");
  }

  @Test
  void shouldRegisterTaskOverTcpAndDeliverViaHttp() throws Exception {
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

    Channel channel = b.connect("localhost", 9975).sync().channel();

    // 1. Authenticate
    AuthHandshake handshake =
        AuthHandshake.newBuilder().setClientId("test-client").setPassword("password").build();
    channel.writeAndFlush(BoomerangEnvelope.newBuilder().setAuthHandshake(handshake).build());

    BoomerangEnvelope authResponse = responses.poll(10, TimeUnit.SECONDS);
    assertThat(authResponse).isNotNull();
    assertThat(authResponse.getAuthResponse().getStatus()).isEqualTo(Status.OK);
    String sessionId = authResponse.getAuthResponse().getSessionId();

    // 2. Register Task
    byte[] testPayload = "hello-world-integration".getBytes();
    Task taskRequest =
        Task.newBuilder()
            .setPayload(ByteString.copyFrom(testPayload))
            .setDelayMs(500) // 500ms delay
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

    // 3. Wait for execution and verify callback
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(receivedPayload.get()).isEqualTo(testPayload);
              assertThat(receivedTaskId.get()).isEqualTo(taskId);
            });

    // 4. Verify task is removed from storage after successful delivery
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(bootstrap.getTimer().get(taskId)).isEmpty();
            });

    channel.close().sync();
  }

  @Test
  void shouldListTasksOverTcp() throws Exception {
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

    Channel channel = b.connect("localhost", 9975).sync().channel();

    // 1. Authenticate as Admin
    AuthHandshake adminHandshake =
        AuthHandshake.newBuilder().setClientId("admin-client").setPassword("admin-pass").build();
    channel.writeAndFlush(BoomerangEnvelope.newBuilder().setAuthHandshake(adminHandshake).build());
    BoomerangEnvelope authResponse = responses.poll(10, TimeUnit.SECONDS);
    Assertions.assertNotNull(authResponse);
    String adminSessionId = authResponse.getAuthResponse().getSessionId();

    // 2. Authenticate as test-client
    channel.close().sync();
    channel = b.connect("localhost", 9975).sync().channel();
    AuthHandshake clientHandshake =
        AuthHandshake.newBuilder().setClientId("test-client").setPassword("password").build();
    channel.writeAndFlush(BoomerangEnvelope.newBuilder().setAuthHandshake(clientHandshake).build());
    authResponse = responses.poll(10, TimeUnit.SECONDS);
    Assertions.assertNotNull(authResponse);
    String clientSessionId = authResponse.getAuthResponse().getSessionId();

    // 3. Register Task for test-client
    Task t1 =
        Task.newBuilder()
            .setPayload(ByteString.copyFrom("p1".getBytes()))
            .setDelayMs(600000) // 10 minutes
            .build();
    channel.writeAndFlush(
        BoomerangEnvelope.newBuilder()
            .setSessionId(clientSessionId)
            .setRegistrationRequest(t1)
            .build());
    responses.poll(10, TimeUnit.SECONDS); // Ignore reg response

    // 4. Register Task for admin-client (recurring)
    channel.close().sync();
    channel = b.connect("localhost", 9975).sync().channel();
    channel.writeAndFlush(BoomerangEnvelope.newBuilder().setAuthHandshake(adminHandshake).build());
    adminSessionId =
        Objects.requireNonNull(responses.poll(10, TimeUnit.SECONDS))
            .getAuthResponse()
            .getSessionId();

    Task t2 =
        Task.newBuilder()
            .setPayload(ByteString.copyFrom("p2".getBytes()))
            .setDelayMs(1200000)
            .setRepeatIntervalMs(60000)
            .build();
    channel.writeAndFlush(
        BoomerangEnvelope.newBuilder()
            .setSessionId(adminSessionId)
            .setRegistrationRequest(t2)
            .build());
    BoomerangEnvelope regResponse = responses.poll(10, TimeUnit.SECONDS);
    Assertions.assertNotNull(regResponse);
    String taskId2 = regResponse.getRegistrationResponse().getTaskId();

    // 5. List all tasks (Admin)
    channel.writeAndFlush(
        BoomerangEnvelope.newBuilder()
            .setSessionId(adminSessionId)
            .setListTasksRequest(ListTasksRequest.newBuilder().setLimit(10).build())
            .build());
    BoomerangEnvelope listResponse = responses.poll(10, TimeUnit.SECONDS);
    Assertions.assertNotNull(listResponse);
    assertThat(listResponse.getListTasksResponse().getStatus()).isEqualTo(Status.OK);
    assertThat(listResponse.getListTasksResponse().getTasksList()).hasSize(2);

    // 6. List with filter (Recurring only)
    channel.writeAndFlush(
        BoomerangEnvelope.newBuilder()
            .setSessionId(adminSessionId)
            .setListTasksRequest(
                ListTasksRequest.newBuilder().setIsRecurring(true).setLimit(10).build())
            .build());
    listResponse = responses.poll(10, TimeUnit.SECONDS);
    Assertions.assertNotNull(listResponse);
    assertThat(listResponse.getListTasksResponse().getTasksList()).hasSize(1);
    assertThat(listResponse.getListTasksResponse().getTasks(0).getTaskId()).isEqualTo(taskId2);

    // 7. Get specific task (Admin)
    channel.writeAndFlush(
        BoomerangEnvelope.newBuilder()
            .setSessionId(adminSessionId)
            .setGetTaskRequest(GetTaskRequest.newBuilder().setTaskId(taskId2).build())
            .build());
    BoomerangEnvelope getResponse = responses.poll(10, TimeUnit.SECONDS);
    Assertions.assertNotNull(getResponse);
    assertThat(getResponse.getGetTaskResponse().getStatus()).isEqualTo(Status.OK);
    assertThat(getResponse.getGetTaskResponse().getTask().getTaskId()).isEqualTo(taskId2);

    // 8. Client listing (should only see their own)
    channel.close().sync();
    channel = b.connect("localhost", 9975).sync().channel();
    channel.writeAndFlush(BoomerangEnvelope.newBuilder().setAuthHandshake(clientHandshake).build());
    clientSessionId =
        Objects.requireNonNull(responses.poll(10, TimeUnit.SECONDS))
            .getAuthResponse()
            .getSessionId();

    channel.writeAndFlush(
        BoomerangEnvelope.newBuilder()
            .setSessionId(clientSessionId)
            .setListTasksRequest(ListTasksRequest.newBuilder().setLimit(10).build())
            .build());
    listResponse = responses.poll(10, TimeUnit.SECONDS);
    Assertions.assertNotNull(listResponse);
    assertThat(listResponse.getListTasksResponse().getTasksList()).hasSize(1);
    assertThat(listResponse.getListTasksResponse().getTasks(0).getClientId())
        .isEqualTo("test-client");

    channel.close().sync();
  }
}
