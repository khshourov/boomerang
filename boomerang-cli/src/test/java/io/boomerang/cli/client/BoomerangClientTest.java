package io.boomerang.cli.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.boomerang.proto.AuthResponse;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.Status;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoomerangClientTest {
  private BoomerangClient client;
  private Channel mockChannel;
  private ChannelPipeline mockPipeline;

  @BeforeEach
  void setUp() throws Exception {
    client = new BoomerangClient("localhost", 9973);
    mockChannel = mock(Channel.class);
    mockPipeline = mock(ChannelPipeline.class);
    when(mockChannel.pipeline()).thenReturn(mockPipeline);

    // Use reflection to set the channel for testing sendRequest/login
    Field channelField = BoomerangClient.class.getDeclaredField("channel");
    channelField.setAccessible(true);
    channelField.set(client, mockChannel);
  }

  @Test
  void shouldLoginSuccessfully() throws Exception {
    // Arrange
    AuthResponse response =
        AuthResponse.newBuilder().setStatus(Status.OK).setSessionId("test-session").build();
    BoomerangEnvelope responseEnv =
        BoomerangEnvelope.newBuilder().setAuthResponse(response).build();

    ChannelFuture mockFuture = mock(ChannelFuture.class);
    when(mockChannel.writeAndFlush(any())).thenReturn(mockFuture);
    when(mockFuture.sync()).thenReturn(mockFuture);

    // Mock the handler being added to the pipeline and completing the future
    when(mockPipeline.addLast(any()))
        .thenAnswer(
            invocation -> {
              BoomerangClientHandler handler = invocation.getArgument(0);
              handler.channelRead0(null, responseEnv);
              return mockPipeline;
            });

    // Act
    boolean success = client.login("user", "pass".toCharArray());

    // Assert
    assertTrue(success);
    verify(mockChannel).writeAndFlush(any(BoomerangEnvelope.class));
  }

  @Test
  void shouldHandleLoginFailure() throws Exception {
    // Arrange
    AuthResponse response = AuthResponse.newBuilder().setStatus(Status.UNAUTHORIZED).build();
    BoomerangEnvelope responseEnv =
        BoomerangEnvelope.newBuilder().setAuthResponse(response).build();

    ChannelFuture mockFuture = mock(ChannelFuture.class);
    when(mockChannel.writeAndFlush(any())).thenReturn(mockFuture);
    when(mockFuture.sync()).thenReturn(mockFuture);

    when(mockPipeline.addLast(any()))
        .thenAnswer(
            invocation -> {
              BoomerangClientHandler handler = invocation.getArgument(0);
              handler.channelRead0(null, responseEnv);
              return mockPipeline;
            });

    // Act
    boolean success = client.login("user", "pass".toCharArray());

    // Assert
    assertFalse(success);
  }

  @Test
  void shouldCloseResources() {
    // Arrange
    EventLoopGroup mockGroup = mock(EventLoopGroup.class);
    try {
      Field groupField = BoomerangClient.class.getDeclaredField("group");
      groupField.setAccessible(true);
      groupField.set(client, mockGroup);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // Act
    client.close();

    // Assert
    verify(mockChannel).close();
    verify(mockGroup).shutdownGracefully();
  }
}
