# ==============================================================================
# Stage 1: Build the Spring Boot application using Maven Wrapper
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install tools needed by Maven wrapper and normalize line endings for Windows checkouts
RUN apk add --no-cache bash curl unzip

# Copy Maven wrapper and POM configuration first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings and grant execution rights to the Maven wrapper
RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw

# Cache dependencies in an intermediate Docker layer
RUN ./mvnw dependency:go-offline -B

# Copy application source code
COPY src ./src

# Build production JAR without running tests (tested during development/CI)
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# Stage 2: Minimal Production JRE Runtime
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Create dedicated non-root user and group for security
RUN addgroup -S documind && adduser -S documind -G documind

# Copy executable JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Set ownership of application files to the non-root user
RUN chown -R documind:documind /app

# Switch to non-root user
USER documind

# Expose Spring Boot application port
EXPOSE 8080

# Configure JVM ergonomics for container environments
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Healthcheck monitoring Spring Boot Actuator endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
