# ==============================================================================
# DocuMindAI - Multi-Stage Root Dockerfile
#
# Primary Deployment Target: Render Web Service (Single-Container Full Stack)
# Render Settings:
#   - Dockerfile Path: ./Dockerfile
#   - Docker Context: .
#
# Architecture:
# - Stage 1 (client-builder): Builds Vite production static assets
# - Stage 2 (server-builder): Builds Spring Boot executable JAR with Maven
# - Stage 3 (backend):        Standalone targetable backend runner
# - Stage 4 (frontend):       Standalone targetable frontend runner
# - Stage 5 (production):     Default final stage for Render; runs Nginx on $PORT
#                             and Spring Boot on loopback 127.0.0.1:8080.
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build the React + Vite Frontend
# ------------------------------------------------------------------------------
FROM node:20-alpine AS client-builder

WORKDIR /app/client

# Copy dependency manifests first for Docker layer caching
COPY client/package.json client/package-lock.json ./
RUN npm ci

# Copy client source code
COPY client/ ./

# Optional build-time argument for API URL (defaults to relative /api reverse proxy)
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# Build production static bundle into dist/
RUN npm run build

# ------------------------------------------------------------------------------
# Stage 2: Build the Spring Boot Application with Maven
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS server-builder

WORKDIR /app/server

# Install tools needed by Maven wrapper and normalize line endings
RUN apk add --no-cache bash curl unzip

# Copy Maven wrapper and POM first for layer caching
COPY server/.mvn/ .mvn/
COPY server/mvnw server/pom.xml ./
RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw

# Cache Maven dependencies in an intermediate layer
RUN ./mvnw dependency:go-offline -B

# Copy server source code and package application JAR
COPY server/src ./src
RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 3: Standalone Backend Runner (Targetable via --target backend)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS backend

WORKDIR /app

RUN addgroup -S documind && adduser -S documind -G documind
COPY --from=server-builder /app/server/target/*.jar app.jar
RUN chown -R documind:documind /app

USER documind
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

# ------------------------------------------------------------------------------
# Stage 4: Standalone Frontend Runner (Targetable via --target frontend)
# ------------------------------------------------------------------------------
FROM nginx:alpine AS frontend

RUN rm -rf /usr/share/nginx/html/*
COPY --from=client-builder /app/client/dist /usr/share/nginx/html
COPY client/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

# ------------------------------------------------------------------------------
# Stage 5 (Default Final Stage): Unified Full-Stack Runner for Render
# Runs Nginx on Render's assigned $PORT (serving static assets & proxying /api)
# and runs Spring Boot on internal 127.0.0.1:8080.
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS production

WORKDIR /app

# Install Nginx, Bash, and network utilities
RUN apk add --no-cache bash nginx curl wget

# Create non-root system user and prepare required directories
RUN addgroup -S documind && adduser -S documind -G documind && \
    mkdir -p /run/nginx /var/log/nginx /usr/share/nginx/html

# Copy built frontend static assets from Stage 1
COPY --from=client-builder /app/client/dist /usr/share/nginx/html

# Copy built Spring Boot JAR from Stage 2
COPY --from=server-builder /app/server/target/*.jar app.jar

# Copy Nginx configuration and entrypoint supervisor script
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/entrypoint.sh /app/entrypoint.sh

# Ensure script has POSIX line endings and execution permissions
RUN sed -i 's/\r$//' /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh && \
    chown -R documind:documind /app

# Default Render port (Render injects $PORT dynamically, typically 10000)
ENV PORT=10000
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=65.0 -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

# Expose Render standard port
EXPOSE 10000 80

# Health check to verify frontend/API availability
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:${PORT:-10000}/healthz || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
