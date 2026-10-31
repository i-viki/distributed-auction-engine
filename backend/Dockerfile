# ==============================================================================
# Distributed Auction Engine - Backend Standalone Dockerfile
# Author: Jayavignesh (https://jayavignesh.dev)
# Multi-Stage Build: Maven 3.9.9 (Temurin 21) -> Alpine JRE 21 Runtime
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build & Dependency Resolution
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache Maven dependencies by copying pom.xml first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source code and compile fat JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Ultra-Lightweight Production JRE 21 Runtime (~180MB)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Jayavignesh <https://jayavignesh.dev>"
LABEL service="distributed-auction-engine-backend"

# Install curl for container health check
RUN apk add --no-cache curl

WORKDIR /app

# Run as non-privileged security user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

# Copy compiled executable JAR from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/distributed-auction-engine-*.jar app.jar

# Expose HTTP REST / WebSocket port
EXPOSE 8080

# Configure health check for container orchestrators (Kubernetes / Docker Compose)
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:8080/v3/api-docs || exit 1

# Environment variables with production JVM ergonomics:
# - Generational ZGC for sub-millisecond GC pause times under high bid churn
# - MaxRAMPercentage=75.0 automatically adapts to container cgroup memory limits
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

