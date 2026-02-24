package io.boomerang.web.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Boomerang Web Backend application.
 *
 * <p>This application provides a REST API for managing Boomerang tasks and sessions. It integrates
 * with {@code boomerang-client-java} to communicate with the core Boomerang server.
 */
@SpringBootApplication
@OpenAPIDefinition(
    info =
        @Info(
            title = "Boomerang Web API",
            version = "1.0",
            description = "REST API for managing Boomerang tasks and sessions"))
public class BoomerangWebApplication {

  /**
   * Main method to start the Spring Boot application.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(BoomerangWebApplication.class, args);
  }
}
