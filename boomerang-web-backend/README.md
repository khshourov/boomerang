# Boomerang Web Backend

This module provides the REST API for the Boomerang system, acting as a bridge between the frontend and the core Boomerang server.

## 💻 Development Commands

The following commands are used for building, testing, and maintaining the `boomerang-web-backend` module. Ensure you are in the project root when running these Gradle commands.

### Build and Format
- **Build the module:** `./gradlew :boomerang-web-backend:build`
- **Apply code formatting:** `./gradlew :boomerang-web-backend:spotlessApply`
- **Check code formatting:** `./gradlew :boomerang-web-backend:spotlessCheck`

### Testing
- **Run Unit Tests (Fast):**
  ```bash
  ./gradlew :boomerang-web-backend:test
  ```
  *Note: These tests are fast and do not require Docker.*

- **Run Integration Tests (Slower):**
  ```bash
  ./gradlew :boomerang-web-backend:intTest
  ```
  *Note: These tests require a running Docker daemon as they use Testcontainers to spin up a live Boomerang server.*

- **Run All Checks:**
  ```bash
  ./gradlew :boomerang-web-backend:check
  ```
  *Note: This runs unit tests, integration tests, and formatting checks.*

## 📖 OpenAPI Specification

When the backend is running (by default on port `8080`), you can access the OpenAPI documentation and Swagger UI for API exploration and testing:

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON Spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

To run the backend locally:
```bash
./gradlew :boomerang-web-backend:bootRun
```

## 🐳 Running Integration Tests

Integration tests build the `boomerang-core` Docker image dynamically. To speed up repeated test runs, ensure your Docker daemon is running and has access to the internet to pull the base `eclipse-temurin` images.

The Boomerang server in these tests is configured with:
- **Exposed Port:** 9973
- **Default Admin Client:** `admin` / `admin123`
- **Master Key:** Dynamically generated in the test or provided via `BOOMERANG_MASTER_KEY` environment variable.
