package io.boomerang.client;

import io.boomerang.proto.ClientDeregistrationRequest;
import io.boomerang.proto.ClientDeregistrationResponse;
import io.boomerang.proto.ClientRegistrationRequest;
import io.boomerang.proto.ClientRegistrationResponse;
import io.boomerang.proto.GetTaskResponse;
import io.boomerang.proto.ListTasksRequest;
import io.boomerang.proto.ListTasksResponse;
import io.boomerang.proto.RegistrationResponse;
import io.boomerang.proto.Task;

/**
 * Synchronous client for interacting with the Boomerang scheduler.
 *
 * @since 0.1.0
 */
public interface BoomerangClient extends AutoCloseable {

  /**
   * Connects to the server.
   *
   * @throws BoomerangException if the connection fails
   */
  void connect() throws BoomerangException;

  /**
   * Authenticates with the server and establishes a session.
   *
   * @param clientId the client identifier
   * @param password the client password
   * @throws BoomerangException if authentication fails
   */
  void login(String clientId, String password) throws BoomerangException;

  /**
   * Registers a new task for future execution.
   *
   * @param task the task to register
   * @return the registration response from the server
   * @throws BoomerangException if registration fails
   */
  RegistrationResponse register(Task task) throws BoomerangException;

  /**
   * Cancels an existing task.
   *
   * @param taskId the unique identifier of the task to cancel
   * @return {@code true} if cancellation was successful, {@code false} otherwise
   * @throws BoomerangException if cancellation fails
   */
  boolean cancel(String taskId) throws BoomerangException;

  /**
   * Retrieves details for a specific task.
   *
   * @param taskId the unique identifier of the task
   * @return the task details response
   * @throws BoomerangException if retrieval fails
   */
  GetTaskResponse getTask(String taskId) throws BoomerangException;

  /**
   * Lists tasks based on the provided filter and pagination criteria.
   *
   * @param request the listing request
   * @return the listing response
   * @throws BoomerangException if listing fails
   */
  ListTasksResponse listTasks(ListTasksRequest request) throws BoomerangException;

  /**
   * Registers a new client (Admin only).
   *
   * @param request the client registration request
   * @return the registration response
   * @throws BoomerangException if registration fails
   */
  ClientRegistrationResponse registerClient(ClientRegistrationRequest request)
      throws BoomerangException;

  /**
   * Deregisters an existing client (Admin only).
   *
   * @param request the client deregistration request
   * @return the deregistration response
   * @throws BoomerangException if deregistration fails
   */
  ClientDeregistrationResponse deregisterClient(ClientDeregistrationRequest request)
      throws BoomerangException;

  /** Closes the client and releases any associated resources. */
  @Override
  void close();
}
