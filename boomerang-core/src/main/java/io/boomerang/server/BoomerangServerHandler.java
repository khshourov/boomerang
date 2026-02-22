package io.boomerang.server;

import io.boomerang.auth.AuthService;
import io.boomerang.proto.BoomerangEnvelope;
import io.boomerang.proto.Status;
import io.boomerang.session.SessionManager;
import io.boomerang.timer.Timer;
import io.boomerang.timer.TimerTask;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty handler that processes incoming {@link BoomerangEnvelope} messages.
 *
 * <p>This handler routes the incoming requests to the appropriate services (Auth, Session, Timer)
 * and sends back the corresponding responses.
 *
 * @since 1.0.0
 */
public class BoomerangServerHandler extends SimpleChannelInboundHandler<BoomerangEnvelope> {
  private static final Logger log = LoggerFactory.getLogger(BoomerangServerHandler.class);
  private final AuthService authService;
  private final SessionManager sessionManager;
  private final Timer timer;

  /**
   * Constructs the server handler with the required services.
   *
   * @param authService the authentication service
   * @param sessionManager the session manager
   * @param timer the scheduling timer
   */
  public BoomerangServerHandler(
      AuthService authService, SessionManager sessionManager, Timer timer) {
    this.authService = authService;
    this.sessionManager = sessionManager;
    this.timer = timer;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, BoomerangEnvelope envelope) {
    log.debug("Received envelope: {}", envelope.getPayloadCase());

    switch (envelope.getPayloadCase()) {
      case AUTH_HANDSHAKE -> handleAuthHandshake(ctx, envelope);
      case REGISTRATION_REQUEST -> handleRegistration(ctx, envelope);
      case CANCELLATION_REQUEST -> handleCancellation(ctx, envelope);
      case SESSION_REFRESH -> handleSessionRefresh(ctx, envelope);
      case CLIENT_REGISTRATION -> handleClientRegistration(ctx, envelope);
      case CLIENT_DEREGISTRATION -> handleClientDeregistration(ctx, envelope);
      case LIST_TASKS_REQUEST -> handleListTasks(ctx, envelope);
      case GET_TASK_REQUEST -> handleGetTask(ctx, envelope);
      default -> {
        log.warn("Unsupported payload type: {}", envelope.getPayloadCase());
      }
    }
  }

  private void handleListTasks(ChannelHandlerContext ctx, BoomerangEnvelope envelope) {
    String sessionId = envelope.getSessionId();
    if (!sessionManager.isValid(sessionId)) {
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setListTasksResponse(
                  io.boomerang.proto.ListTasksResponse.newBuilder()
                      .setStatus(Status.UNAUTHORIZED)
                      .setErrorMessage("Invalid or expired session")
                      .build())
              .build());
      return;
    }

    String callerClientId = sessionManager.getClientId(sessionId);
    boolean isAdmin = authService.isAdmin(callerClientId);

    var request = envelope.getListTasksRequest();
    String targetClientId = request.getClientId();

    // Security check: regular clients can only list their own tasks.
    if (!isAdmin) {
      if (!targetClientId.isEmpty() && !targetClientId.equals(callerClientId)) {
        ctx.writeAndFlush(
            BoomerangEnvelope.newBuilder()
                .setListTasksResponse(
                    io.boomerang.proto.ListTasksResponse.newBuilder()
                        .setStatus(Status.UNAUTHORIZED)
                        .setErrorMessage("Cannot list tasks for other clients")
                        .build())
                .build());
        return;
      }
      targetClientId = callerClientId;
    } else if (targetClientId.isEmpty()) {
      // Admins list all tasks if no client ID is specified.
      targetClientId = null;
    }

    long scheduledAfter = request.getScheduledAfter();
    long scheduledBefore =
        request.getScheduledBefore() > 0 ? request.getScheduledBefore() : Long.MAX_VALUE;
    Boolean isRecurring = request.hasIsRecurring() ? request.getIsRecurring() : null;
    int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), 1000) : 100;
    String nextToken = request.getNextToken();

    try {
      var result =
          timer.list(
              targetClientId, scheduledAfter, scheduledBefore, isRecurring, limit, nextToken);

      var responseBuilder = io.boomerang.proto.ListTasksResponse.newBuilder().setStatus(Status.OK);
      result.items().forEach(task -> responseBuilder.addTasks(ModelMapper.map(task)));
      if (result.nextToken() != null) {
        responseBuilder.setNextToken(result.nextToken());
      }

      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder().setListTasksResponse(responseBuilder.build()).build());
    } catch (Exception e) {
      log.error("Failed to list tasks: {}", e.getMessage(), e);
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setListTasksResponse(
                  io.boomerang.proto.ListTasksResponse.newBuilder()
                      .setStatus(Status.ERROR)
                      .setErrorMessage(e.getMessage())
                      .build())
              .build());
    }
  }

  private void handleGetTask(ChannelHandlerContext ctx, BoomerangEnvelope envelope) {
    String sessionId = envelope.getSessionId();
    if (!sessionManager.isValid(sessionId)) {
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setGetTaskResponse(
                  io.boomerang.proto.GetTaskResponse.newBuilder()
                      .setStatus(Status.UNAUTHORIZED)
                      .setErrorMessage("Invalid or expired session")
                      .build())
              .build());
      return;
    }

    String callerClientId = sessionManager.getClientId(sessionId);
    boolean isAdmin = authService.isAdmin(callerClientId);

    var request = envelope.getGetTaskRequest();
    var taskOpt = timer.get(request.getTaskId());

    if (taskOpt.isPresent()) {
      var task = taskOpt.get();
      // Security check: regular clients can only see their own tasks.
      if (!isAdmin && !task.getClientId().equals(callerClientId)) {
        ctx.writeAndFlush(
            BoomerangEnvelope.newBuilder()
                .setGetTaskResponse(
                    io.boomerang.proto.GetTaskResponse.newBuilder()
                        .setStatus(Status.UNAUTHORIZED)
                        .setErrorMessage("Access denied to task")
                        .build())
                .build());
        return;
      }

      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setGetTaskResponse(
                  io.boomerang.proto.GetTaskResponse.newBuilder()
                      .setStatus(Status.OK)
                      .setTask(ModelMapper.map(task))
                      .build())
              .build());
    } else {
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setGetTaskResponse(
                  io.boomerang.proto.GetTaskResponse.newBuilder()
                      .setStatus(Status.ERROR)
                      .setErrorMessage("Task not found")
                      .build())
              .build());
    }
  }

  private void handleAuthHandshake(ChannelHandlerContext ctx, BoomerangEnvelope envelope) {
    var handshake = envelope.getAuthHandshake();
    var sessionOpt = authService.authenticate(handshake.getClientId(), handshake.getPassword());

    var responseBuilder = io.boomerang.proto.AuthResponse.newBuilder();
    if (sessionOpt.isPresent()) {
      var session = sessionOpt.get();
      responseBuilder
          .setSessionId(session.sessionId())
          .setStatus(Status.OK)
          .setExpiresAtMs(session.expiresAt().toEpochMilli());
    } else {
      responseBuilder.setStatus(Status.UNAUTHORIZED).setErrorMessage("Invalid credentials");
    }

    ctx.writeAndFlush(
        BoomerangEnvelope.newBuilder().setAuthResponse(responseBuilder.build()).build());
  }

  private void handleRegistration(ChannelHandlerContext ctx, BoomerangEnvelope envelope) {
    String sessionId = envelope.getSessionId();
    if (!sessionManager.isValid(sessionId)) {
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setRegistrationResponse(
                  io.boomerang.proto.RegistrationResponse.newBuilder()
                      .setStatus(Status.UNAUTHORIZED)
                      .setErrorMessage("Invalid or expired session")
                      .build())
              .build());
      return;
    }

    var request = envelope.getRegistrationRequest();
    // In TieredTimer, the 'dispatcher' handles the execution logic.
    // The Runnable here is a placeholder for internal tasks if needed.
    var task =
        new TimerTask(
            UUID.randomUUID().toString(),
            sessionManager.getClientId(sessionId),
            request.getDelayMs(),
            request.getPayload().toByteArray(),
            request.getRepeatIntervalMs(),
            () -> {});

    timer.add(task);

    ctx.writeAndFlush(
        BoomerangEnvelope.newBuilder()
            .setRegistrationResponse(
                io.boomerang.proto.RegistrationResponse.newBuilder()
                    .setTaskId(task.getTaskId())
                    .setStatus(Status.OK)
                    .setScheduledTimeMs(task.getExpirationMs())
                    .build())
            .build());
  }

  private void handleCancellation(ChannelHandlerContext ctx, BoomerangEnvelope envelope) {
    String sessionId = envelope.getSessionId();
    if (!sessionManager.isValid(sessionId)) {
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setCancellationResponse(
                  io.boomerang.proto.CancellationResponse.newBuilder()
                      .setStatus(Status.UNAUTHORIZED)
                      .setErrorMessage("Invalid or expired session")
                      .build())
              .build());
      return;
    }

    var request = envelope.getCancellationRequest();
    timer.cancel(request.getTaskId());

    ctx.writeAndFlush(
        BoomerangEnvelope.newBuilder()
            .setCancellationResponse(
                io.boomerang.proto.CancellationResponse.newBuilder().setStatus(Status.OK).build())
            .build());
  }

  private void handleSessionRefresh(ChannelHandlerContext ctx, BoomerangEnvelope envelope) {
    String sessionId = envelope.getSessionId();
    var sessionOpt = sessionManager.refreshSession(sessionId);

    var responseBuilder = io.boomerang.proto.SessionRefreshResponse.newBuilder();
    if (sessionOpt.isPresent()) {
      responseBuilder
          .setStatus(Status.OK)
          .setNewExpiresAtMs(sessionOpt.get().expiresAt().toEpochMilli());
    } else {
      responseBuilder.setStatus(Status.UNAUTHORIZED);
    }

    ctx.writeAndFlush(
        BoomerangEnvelope.newBuilder().setSessionRefreshResponse(responseBuilder.build()).build());
  }

  private void handleClientRegistration(ChannelHandlerContext ctx, BoomerangEnvelope envelope) {
    String sessionId = envelope.getSessionId();
    String clientId = sessionManager.getClientId(sessionId);
    if (clientId == null || !authService.isAdmin(clientId)) {
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setClientRegistrationResponse(
                  io.boomerang.proto.ClientRegistrationResponse.newBuilder()
                      .setStatus(Status.UNAUTHORIZED)
                      .setErrorMessage("Only admins can register new clients")
                      .build())
              .build());
      return;
    }

    var request = envelope.getClientRegistration();
    try {
      authService.registerClient(
          request.getClientId(),
          request.getPassword(),
          request.getIsAdmin(),
          ModelMapper.map(request.getCallback()),
          ModelMapper.map(request.getRetry()),
          ModelMapper.map(request.getDlq()));

      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setClientRegistrationResponse(
                  io.boomerang.proto.ClientRegistrationResponse.newBuilder()
                      .setStatus(Status.OK)
                      .build())
              .build());
    } catch (Exception e) {
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setClientRegistrationResponse(
                  io.boomerang.proto.ClientRegistrationResponse.newBuilder()
                      .setStatus(Status.ERROR)
                      .setErrorMessage(e.getMessage())
                      .build())
              .build());
    }
  }

  private void handleClientDeregistration(ChannelHandlerContext ctx, BoomerangEnvelope envelope) {
    String sessionId = envelope.getSessionId();
    String clientId = sessionManager.getClientId(sessionId);
    if (clientId == null || !authService.isAdmin(clientId)) {
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setClientDeregistrationResponse(
                  io.boomerang.proto.ClientDeregistrationResponse.newBuilder()
                      .setStatus(Status.UNAUTHORIZED)
                      .setErrorMessage("Only admins can deregister clients")
                      .build())
              .build());
      return;
    }

    var request = envelope.getClientDeregistration();
    try {
      authService.deregisterClient(request.getClientId());
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setClientDeregistrationResponse(
                  io.boomerang.proto.ClientDeregistrationResponse.newBuilder()
                      .setStatus(Status.OK)
                      .build())
              .build());
    } catch (Exception e) {
      ctx.writeAndFlush(
          BoomerangEnvelope.newBuilder()
              .setClientDeregistrationResponse(
                  io.boomerang.proto.ClientDeregistrationResponse.newBuilder()
                      .setStatus(Status.ERROR)
                      .setErrorMessage(e.getMessage())
                      .build())
              .build());
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("Error in server handler: {}", cause.getMessage(), cause);
    ctx.close();
  }
}
