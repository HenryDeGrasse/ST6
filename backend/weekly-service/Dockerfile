# ---------------------------------------------------------------------------
# Multi-stage build for weekly-service
# PRD §12.1 (D2): Container image — eclipse-temurin:21-jre-alpine + fat JAR
# ---------------------------------------------------------------------------

# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Gradle wrapper and build files first (for layer caching)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --version

# Copy source and build
COPY src/ src/
COPY config/ config/
RUN ./gradlew bootJar -x test -x checkstyleMain -x checkstyleTest --no-daemon

# ── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Security: run as non-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the fat JAR
COPY --from=builder /build/build/libs/*.jar app.jar

# Health check endpoint for container orchestrators
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

# Expose the API port
EXPOSE 8080

USER appuser

# Virtual threads enabled via JVM flag (Java 21 / PRD §9.2)
ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djdk.virtualThreadScheduler.parallelism=4", \
    "-jar", "app.jar"]
