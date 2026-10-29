# syntax=docker/dockerfile:1

# Stage 1: Build the Spring Boot application
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

# Copy maven wrapper and project descriptors
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings for cross-platform compatibility and set execution permission
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Download dependencies for layer caching
RUN ./mvnw dependency:go-offline -B || true

# Copy application source code and package JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:25-jre AS runner
WORKDIR /app

# Create non-root user and assign permissions
RUN groupadd -r appuser && useradd -r -u 10001 -g appuser -d /app -s /bin/false appuser \
    && chown -R appuser:appuser /app

# Copy executable jar from builder stage
COPY --from=builder --chown=appuser:appuser /workspace/target/inventory-service-*.jar app.jar

# Switch to non-root user
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
