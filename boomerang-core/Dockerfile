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

# Download dependencies (this layer will be cached unless build files change)
RUN ./gradlew :boomerang-core:dependencies --no-daemon || true

# Copy source code for necessary modules
COPY boomerang-proto/src boomerang-proto/src
COPY boomerang-core/src boomerang-core/src

# Build only the boomerang-core module
RUN ./gradlew :boomerang-core:installDist --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install netcat for health checks
RUN apt-get update && apt-get install -y netcat-openbsd && rm -rf /var/lib/apt/lists/*

# Create a non-root user for security
RUN groupadd -r boomerang && useradd -r -g boomerang boomerang \
    && mkdir -p /app/data && chown -R boomerang:boomerang /app/data
USER boomerang

# Copy the installed distribution from the build stage
COPY --from=build --chown=boomerang:boomerang /app/boomerang-core/build/install/boomerang-core .

# Expose the default Boomerang port
EXPOSE 9973

# Ensure BOOMERANG_MASTER_KEY is available at runtime.
# The application will fail to initialize if this is missing.
ENTRYPOINT ["bin/boomerang-core"]
