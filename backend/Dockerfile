# ============================================================
# AI Job Pilot  — Multi-stage Docker Build
# ============================================================

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy the POM first for dependency caching
COPY pom.xml ./

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
# Keep a glibc-based runtime: DJL tokenizers and ONNX load native Linux libraries.
# Alpine previously failed to load libtokenizers.so (missing libstdc++.so.6).
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup -m appuser

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Create upload directory
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
