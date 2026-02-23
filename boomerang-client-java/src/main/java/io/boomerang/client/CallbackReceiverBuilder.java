package io.boomerang.client;

import java.util.Objects;

/**
 * A builder class for creating instances of {@link CallbackReceiver} based on the desired protocol.
 *
 * @since 0.1.0
 */
public class CallbackReceiverBuilder {
  private int port;
  private String httpPath = "/callback";
  private CallbackHandler handler;

  /**
   * Sets the port on which the receiver will listen.
   *
   * @param port the port number
   * @return this builder instance
   */
  public CallbackReceiverBuilder withPort(int port) {
    this.port = port;
    return this;
  }

  /**
   * Sets the path for the HTTP receiver.
   *
   * @param path the path string
   * @return this builder instance
   */
  public CallbackReceiverBuilder withHttpPath(String path) {
    this.httpPath = path;
    return this;
  }

  /**
   * Sets the handler for processing incoming callbacks.
   *
   * @param handler the callback handler implementation
   * @return this builder instance
   */
  public CallbackReceiverBuilder withHandler(CallbackHandler handler) {
    this.handler = handler;
    return this;
  }

  /**
   * Builds and returns a TCP-based callback receiver.
   *
   * @return a new TCP callback receiver
   */
  public CallbackReceiver buildTcp() {
    validate();
    return new TcpCallbackReceiver(port, handler);
  }

  /**
   * Builds and returns a UDP-based callback receiver.
   *
   * @return a new UDP callback receiver
   */
  public CallbackReceiver buildUdp() {
    validate();
    return new UdpCallbackReceiver(port, handler);
  }

  /**
   * Builds and returns an HTTP-based callback receiver.
   *
   * @return a new HTTP callback receiver
   */
  public CallbackReceiver buildHttp() {
    validate();
    return new HttpCallbackReceiver(port, httpPath, handler);
  }

  /**
   * Builds and returns a gRPC-based callback receiver.
   *
   * @return a new gRPC callback receiver
   */
  public CallbackReceiver buildGrpc() {
    validate();
    return new GrpcCallbackReceiver(port, handler);
  }

  private void validate() {
    Objects.requireNonNull(handler, "CallbackHandler must not be null");
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("Invalid port: " + port);
    }
  }
}
