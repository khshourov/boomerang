package io.boomerang.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.boomerang.proto.RegistrationResponse;
import io.boomerang.proto.Status;
import io.boomerang.proto.Task;
import org.junit.jupiter.api.Test;

class SmartBoomerangClientTest {

  @Test
  void testAutomaticLogin() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "user", "pass");

    Task task = Task.getDefaultInstance();
    RegistrationResponse response = RegistrationResponse.newBuilder().setStatus(Status.OK).build();
    when(mockClient.register(task)).thenReturn(response);

    smartClient.register(task);

    verify(mockClient).login("user", "pass");
    verify(mockClient).register(task);
  }

  @Test
  void testRetryOnSessionExpired() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "user", "pass");

    Task task = Task.getDefaultInstance();
    RegistrationResponse okResponse =
        RegistrationResponse.newBuilder().setStatus(Status.OK).build();

    // First call throws SESSION_EXPIRED
    when(mockClient.register(task))
        .thenThrow(new BoomerangException(Status.SESSION_EXPIRED, "expired"))
        .thenReturn(okResponse);

    smartClient.register(task);

    // Initial login + re-login after expiration
    verify(mockClient, times(2)).login("user", "pass");
    verify(mockClient, times(2)).register(task);
  }

  @Test
  void testRetryOnUnauthorized() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "user", "pass");

    Task task = Task.getDefaultInstance();
    RegistrationResponse okResponse =
        RegistrationResponse.newBuilder().setStatus(Status.OK).build();

    // First call throws UNAUTHORIZED
    when(mockClient.register(task))
        .thenThrow(new BoomerangException(Status.UNAUTHORIZED, "expired"))
        .thenReturn(okResponse);

    smartClient.register(task);

    // Initial login + re-login after UNAUTHORIZED
    verify(mockClient, times(2)).login("user", "pass");
    verify(mockClient, times(2)).register(task);
  }

  @Test
  void testNoRetryOnNoStatus() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "u", "p");
    when(mockClient.register(io.boomerang.proto.Task.getDefaultInstance()))
        .thenThrow(new BoomerangException("COMMUNICATION_ERROR"));

    org.junit.jupiter.api.Assertions.assertThrows(
        BoomerangException.class,
        () -> {
          smartClient.register(io.boomerang.proto.Task.getDefaultInstance());
        });

    verify(mockClient, times(1)).login("u", "p");
  }

  @Test
  void testCloseDelegation() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "u", "p");
    smartClient.close();
    verify(mockClient).close();
  }

  @Test
  void testConnectDelegation() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "u", "p");
    smartClient.connect();
    verify(mockClient).connect();
    verify(mockClient).login("u", "p");
  }

  @Test
  void testCancelDelegation() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "u", "p");
    when(mockClient.cancel("t1")).thenReturn(true);
    assertTrue(smartClient.cancel("t1"));
    verify(mockClient).cancel("t1");
  }

  @Test
  void testGetTaskDelegation() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "u", "p");
    io.boomerang.proto.GetTaskResponse response =
        io.boomerang.proto.GetTaskResponse.newBuilder().setStatus(Status.OK).build();
    when(mockClient.getTask("t1")).thenReturn(response);
    assertEquals(response, smartClient.getTask("t1"));
    verify(mockClient).getTask("t1");
  }

  @Test
  void testListTasksDelegation() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "u", "p");
    io.boomerang.proto.ListTasksRequest request =
        io.boomerang.proto.ListTasksRequest.newBuilder().build();
    io.boomerang.proto.ListTasksResponse response =
        io.boomerang.proto.ListTasksResponse.newBuilder().setStatus(Status.OK).build();
    when(mockClient.listTasks(request)).thenReturn(response);
    assertEquals(response, smartClient.listTasks(request));
    verify(mockClient).listTasks(request);
  }

  @Test
  void testRegisterClientDelegation() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "u", "p");
    io.boomerang.proto.ClientRegistrationRequest request =
        io.boomerang.proto.ClientRegistrationRequest.newBuilder().build();
    io.boomerang.proto.ClientRegistrationResponse response =
        io.boomerang.proto.ClientRegistrationResponse.newBuilder().setStatus(Status.OK).build();
    when(mockClient.registerClient(request)).thenReturn(response);
    assertEquals(response, smartClient.registerClient(request));
    verify(mockClient).registerClient(request);
  }

  @Test
  void testDeregisterClientDelegation() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "u", "p");
    io.boomerang.proto.ClientDeregistrationRequest request =
        io.boomerang.proto.ClientDeregistrationRequest.newBuilder().build();
    io.boomerang.proto.ClientDeregistrationResponse response =
        io.boomerang.proto.ClientDeregistrationResponse.newBuilder().setStatus(Status.OK).build();
    when(mockClient.deregisterClient(request)).thenReturn(response);
    assertEquals(response, smartClient.deregisterClient(request));
    verify(mockClient).deregisterClient(request);
  }

  @Test
  void testGetSessionIdDelegation() {
    BoomerangClient mockClient = mock(BoomerangClient.class);
    SmartBoomerangClient smartClient = new SmartBoomerangClient(mockClient, "u", "p");
    when(mockClient.getSessionId()).thenReturn("session-123");
    assertEquals("session-123", smartClient.getSessionId());
    verify(mockClient).getSessionId();
  }
}
