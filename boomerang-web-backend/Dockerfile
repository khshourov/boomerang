# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy gradle wrapper and root configuration
COPY gradlew .
COPY gradlew.bat .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy subproject build files to cache dependencies
COPY boomerang-proto/build.gradle.kts boomerang-proto/
COPY boomerang-core/build.gradle.kts boomerang-core/
COPY boomerang-client-java/build.gradle.kts boomerang-client-java/
COPY boomerang-cli/build.gradle.kts boomerang-cli/
COPY boomerang-web-backend/build.gradle.kts boomerang-web-backend/

# Copy necessary modules source
COPY boomerang-proto/src boomerang-proto/src
COPY boomerang-client-java/src boomerang-client-java/src
COPY boomerang-web-backend/src boomerang-web-backend/src

# Build only the boomerang-web-backend module
RUN ./gradlew :boomerang-web-backend:build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the built JAR file
COPY --from=build /app/boomerang-web-backend/build/libs/boomerang-web-backend-*.jar app.jar

# Expose the backend port
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
