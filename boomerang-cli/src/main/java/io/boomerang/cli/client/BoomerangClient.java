package io.boomerang.cli.client;

import io.boomerang.proto.AuthHandshake;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.Status;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty-based client for communicating with the Boomerang server.
 *
 * @since 0.1.0
 */
public class BoomerangClient implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(BoomerangClient.class);
  private final String host;
  private final int port;
  private EventLoopGroup group;
  private Channel channel;
  private String sessionId;

  /**
   * Constructs a client for the given host and port.
   *
   * @param host the server host
   * @param port the server port
   */
  public BoomerangClient(String host, int port) {
    this.host = host;
    this.port = port;
  }

  /**
   * Connects to the server.
   *
   * @throws InterruptedException if the connection is interrupted
   */
  public void connect() throws InterruptedException {
    group = new NioEventLoopGroup();
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
              }
            });

    ChannelFuture f = b.connect(host, port).sync();
    channel = f.channel();
    log.debug("Connected to {}:{}", host, port);
  }

  /**
   * Performs authentication and establishes a session.
   *
   * @param clientId the client identifier
   * @param password the client password as a char array
   * @return {@code true} if authentication was successful, {@code false} otherwise
   * @throws Exception if an error occurs during authentication
   */
  public boolean login(String clientId, char[] password) throws Exception {
    AuthHandshake handshake =
        AuthHandshake.newBuilder()
            .setClientId(clientId)
            .setPasswordBytes(
                com.google.protobuf.ByteString.copyFrom(
                    StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))))
            .build();
    BoomerangEnvelope envelope = BoomerangEnvelope.newBuilder().setAuthHandshake(handshake).build();

    BoomerangEnvelope response = sendRequest(envelope);
    if (response.hasAuthResponse() && response.getAuthResponse().getStatus() == Status.OK) {
      this.sessionId = response.getAuthResponse().getSessionId();
      return true;
    }
    return false;
  }

  /**
   * Sends a request to the server and waits for a response.
   *
   * @param envelope the request envelope
   * @return the response envelope from the server
   * @throws Exception if an error occurs during communication
   */
  public BoomerangEnvelope sendRequest(BoomerangEnvelope envelope) throws Exception {
    // Add session ID to the envelope if available and not already set
    if (sessionId != null && envelope.getSessionId().isEmpty()) {
      envelope = envelope.toBuilder().setSessionId(sessionId).build();
    }

    BoomerangClientHandler handler = new BoomerangClientHandler();
    channel.pipeline().addLast(handler);
    try {
      channel.writeAndFlush(envelope).sync();
      return handler.getResponseFuture().get(10, TimeUnit.SECONDS);
    } finally {
      channel.pipeline().remove(handler);
    }
  }

  @Override
  public void close() {
    if (channel != null) {
      channel.close();
    }
    if (group != null) {
      group.shutdownGracefully();
    }
  }
}
