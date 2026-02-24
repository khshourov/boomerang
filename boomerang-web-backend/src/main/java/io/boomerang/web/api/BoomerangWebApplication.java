package io.boomerang.web.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
    info =
        @Info(
            title = "Boomerang Web API",
            version = "1.0",
            description = "REST API for managing Boomerang tasks and sessions"))
public class BoomerangWebApplication {
  public static void main(String[] args) {
    SpringApplication.run(BoomerangWebApplication.class, args);
  }
}
