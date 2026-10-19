# ==============================================================================
# Stage 1: Build the React + Vite Frontend
# ==============================================================================
FROM node:20-alpine AS builder

WORKDIR /app

# Copy dependency specifications first for Docker layer caching
COPY package.json package-lock.json ./

# Install dependencies cleanly using lockfile
RUN npm ci

# Copy application source code
COPY . .

# Optional build-time argument for backend API URL
# Defaults to empty string to use relative reverse-proxied /api routes
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# Build production bundle
RUN npm run build

# ==============================================================================
# Stage 2: Serve Production Assets via Nginx
# ==============================================================================
FROM nginx:alpine AS runner

# Remove default Nginx HTML website
RUN rm -rf /usr/share/nginx/html/*

# Copy built distribution from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration with SPA routing and API reverse proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose standard HTTP port
EXPOSE 80

# Health check to ensure Nginx is responding
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
