package io.boomerang.server;

import io.boomerang.auth.AuthService;
import io.boomerang.config.ServerConfig;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.session.SessionManager;
import io.boomerang.timer.Timer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty-based TCP server for Boomerang.
 *
 * <p>This server handles inbound task registration, cancellation, and authentication using Protobuf
 * over TCP.
 *
 * @since 1.0.0
 */
public class BoomerangServer {
  private static final Logger log = LoggerFactory.getLogger(BoomerangServer.class);
  private final ServerConfig config;
  private final AuthService authService;
  private final SessionManager sessionManager;
  private final Timer timer;
  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private EventExecutorGroup businessGroup;

  /**
   * Constructs the server with required services and configuration.
   *
   * @param config the server configuration
   * @param authService the authentication service
   * @param sessionManager the session manager
   * @param timer the scheduling timer
   */
  public BoomerangServer(
      ServerConfig config, AuthService authService, SessionManager sessionManager, Timer timer) {
    this.config = config;
    this.authService = authService;
    this.sessionManager = sessionManager;
    this.timer = timer;
  }

  /**
   * Starts the TCP server.
   *
   * @throws InterruptedException if the server is interrupted while starting
   */
  public void start() throws InterruptedException {
    bossGroup = new NioEventLoopGroup(config.getNettyBossThreads());
    workerGroup = new NioEventLoopGroup(config.getNettyWorkerThreads());
    businessGroup = new DefaultEventExecutorGroup(config.getNettyBusinessThreads());

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
                // Use business logic executor for the handler
                ch.pipeline()
                    .addLast(
                        businessGroup,
                        new BoomerangServerHandler(authService, sessionManager, timer));
              }
            });

    int port = config.getServerPort();
    log.info("Starting Boomerang TCP server on port {}...", port);
    b.bind(port).sync();
    log.info("Boomerang TCP server started.");
  }

  /** Shuts down the server and its event loops. */
  public void stop() {
    log.info("Stopping Boomerang TCP server...");
    if (bossGroup != null) {
      bossGroup.shutdownGracefully();
    }
    if (workerGroup != null) {
      workerGroup.shutdownGracefully();
    }
    if (businessGroup != null) {
      businessGroup.shutdownGracefully();
    }
    log.info("Boomerang TCP server stopped.");
  }
}
