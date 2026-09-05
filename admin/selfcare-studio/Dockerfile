# Multi-stage build for OMOBIO Selfcare Studio
# Stage 1: Build the React app
FROM node:20-alpine AS builder
WORKDIR /app

# Copy package files
COPY package*.json ./
RUN npm install --legacy-peer-deps

# Copy source
COPY . .

# Build-time environment overrides (injected via --build-arg for target env).
ARG VITE_API_GATEWAY_URL
ARG VITE_PUBLIC_API_BASE_URL
ARG VITE_ADMIN_PORTAL_URL
ARG VITE_OBSERVABILITY_URL
ENV VITE_API_GATEWAY_URL=$VITE_API_GATEWAY_URL \
    VITE_PUBLIC_API_BASE_URL=$VITE_PUBLIC_API_BASE_URL \
    VITE_ADMIN_PORTAL_URL=$VITE_ADMIN_PORTAL_URL \
    VITE_OBSERVABILITY_URL=$VITE_OBSERVABILITY_URL

# Build for production
RUN npm run build

# Stage 2: Serve via nginx
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Copy built assets
COPY --from=builder /app/dist .

# Copy nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
