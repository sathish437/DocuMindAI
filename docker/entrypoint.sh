#!/usr/bin/env bash
set -e

# Render provides the PORT environment variable (typically 10000)
# Default to 10000 if not set, or 80 for standard HTTP
PORT="${PORT:-10000}"
echo "[DocuMindAI] Initializing container with public PORT=${PORT}..."

# Replace __PORT__ placeholder in nginx.conf with the actual assigned port
sed -i "s/__PORT__/${PORT}/g" /etc/nginx/nginx.conf

# Ensure required runtime directories exist
mkdir -p /run/nginx /var/log/nginx /usr/share/nginx/html

# Start Spring Boot backend in background on internal port 8080 (loopback only)
echo "[DocuMindAI] Starting Spring Boot backend on 127.0.0.1:8080..."
java ${JAVA_TOOL_OPTIONS:-"-XX:+UseContainerSupport -XX:MaxRAMPercentage=65.0 -XX:+TieredCompilation -XX:TieredStopAtLevel=1"} \
     -jar /app/app.jar \
     --server.port=8080 \
     --server.address=127.0.0.1 &
BACKEND_PID=$!

# Wait for Spring Boot to be healthy before exposing public traffic via Nginx
# On Render free tier cold starts with JVM startup and database handshake,
# Spring Boot can take 60-90+ seconds to become ready.
echo "[DocuMindAI] Waiting for Spring Boot backend to become ready..."
BACKEND_HEALTHY=false
for i in $(seq 1 120); do
    if wget -q -O - http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q "UP"; then
        echo "[DocuMindAI] Backend is healthy and ready to accept requests (elapsed ${i}s)."
        BACKEND_HEALTHY=true
        break
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        echo "[DocuMindAI] FATAL: Spring Boot backend failed to start and exited unexpectedly."
        exit 1
    fi
    sleep 1
done

if [ "$BACKEND_HEALTHY" != "true" ]; then
    echo "[DocuMindAI] WARNING: Backend health check did not report UP within 120s, but backend process is still running. Starting Nginx..."
fi

# Start Nginx in background listening on assigned public PORT
echo "[DocuMindAI] Starting Nginx on port ${PORT}..."
nginx -g "daemon off;" &
NGINX_PID=$!

# Signal handler for graceful shutdown on SIGTERM / SIGINT
cleanup() {
    echo "[DocuMindAI] Received termination signal. Initiating graceful shutdown..."
    kill -TERM "$BACKEND_PID" 2>/dev/null || true
    kill -QUIT "$NGINX_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
    wait "$NGINX_PID" 2>/dev/null || true
    echo "[DocuMindAI] All services stopped."
    exit 0
}

trap cleanup SIGTERM SIGINT

# Monitor both processes: if either process terminates, shut down cleanly
while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$NGINX_PID" 2>/dev/null; do
    sleep 2
done

echo "[DocuMindAI] A service exited unexpectedly. Shutting down container."
cleanup
