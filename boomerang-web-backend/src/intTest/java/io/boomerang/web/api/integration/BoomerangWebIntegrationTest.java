package io.boomerang.web.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.boomerang.web.api.dto.GetTaskResponse;
import io.boomerang.web.api.dto.ListTasksResponse;
import io.boomerang.web.api.dto.LoginRequest;
import io.boomerang.web.api.dto.LoginResponse;
import io.boomerang.web.api.dto.TaskRequest;
import io.boomerang.web.api.dto.TaskResponse;
import java.nio.file.Paths;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BoomerangWebIntegrationTest {

  @Container
  static GenericContainer<?> boomerangServer =
      new GenericContainer<>(
              new ImageFromDockerfile("boomerang-server-test", false)
                  .withFileFromPath(".", Paths.get("..").toAbsolutePath().normalize())
                  .withDockerfilePath("boomerang-core/Dockerfile"))
          .withExposedPorts(9973)
          .withEnv("BOOMERANG_MASTER_KEY", "dwCMAVgSCw7RuCXongyN0eoI3l1YpXEfS1MDEjsU60I=")
          .waitingFor(Wait.forLogMessage(".*Boomerang TCP server started.*", 1));

  @LocalServerPort private int localPort;

  private final RestTemplate restTemplate = new RestTemplate();

  private String getBaseUrl() {
    return "http://localhost:" + localPort;
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("boomerang.server.host", boomerangServer::getHost);
    registry.add("boomerang.server.port", () -> boomerangServer.getMappedPort(9973));
  }

  @Test
  void testAuthAndTaskFlow() throws Exception {
    // 1. Login
    LoginRequest loginRequest = new LoginRequest("admin", "admin123");
    ResponseEntity<LoginResponse> loginResponse =
        restTemplate.postForEntity(getBaseUrl() + "/api/login", loginRequest, LoginResponse.class);

    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    String sessionId = loginResponse.getBody().sessionId();
    assertThat(sessionId).isNotNull();

    // 2. Register Task
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Boomerang-Session-Id", sessionId);
    headers.setContentType(MediaType.APPLICATION_JSON);

    TaskRequest taskRequest =
        new TaskRequest(Base64.getEncoder().encodeToString("test-payload".getBytes()), 10000L, 0L);

    HttpEntity<TaskRequest> request = new HttpEntity<>(taskRequest, headers);
    ResponseEntity<TaskResponse> regResponse =
        restTemplate.postForEntity(getBaseUrl() + "/api/tasks", request, TaskResponse.class);

    assertThat(regResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(regResponse.getBody().status()).isEqualTo("OK");
    String taskId = regResponse.getBody().taskId();

    // 3. List Tasks
    HttpEntity<Void> listRequest = new HttpEntity<>(headers);
    ResponseEntity<ListTasksResponse> listResponse =
        restTemplate.exchange(
            getBaseUrl() + "/api/tasks", HttpMethod.GET, listRequest, ListTasksResponse.class);

    assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listResponse.getBody().status()).isEqualTo("OK");
    assertThat(listResponse.getBody().tasks()).isNotEmpty();

    // 4. Get Task
    HttpEntity<Void> getRequest = new HttpEntity<>(headers);
    ResponseEntity<GetTaskResponse> getResponse =
        restTemplate.exchange(
            getBaseUrl() + "/api/tasks/" + taskId,
            HttpMethod.GET,
            getRequest,
            GetTaskResponse.class);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody().status()).isEqualTo("OK");
    assertThat(getResponse.getBody().task().taskId()).isEqualTo(taskId);

    // 5. Cancel Task
    ResponseEntity<Boolean> cancelResponse =
        restTemplate.exchange(
            getBaseUrl() + "/api/tasks/" + taskId, HttpMethod.DELETE, getRequest, Boolean.class);

    assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(cancelResponse.getBody()).isTrue();
  }

  @Test
  void shouldAccessOpenApiJson() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(getBaseUrl() + "/v3/api-docs", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"title\":\"Boomerang Web API\"");
  }

  @Test
  void shouldAccessSwaggerUi() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(getBaseUrl() + "/swagger-ui/index.html", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("<div id=\"swagger-ui\">");
  }
}
