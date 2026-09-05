#!/usr/bin/env bash
# build-images.sh — Build all 19 OMOBIO microservice Docker images locally
# Usage: ./scripts/build-images.sh [--no-cache]
#
# Prerequisites:
#   - Docker Desktop running
#   - Java 25 available (or will be installed inside build image)
#
# Images are tagged: omobio/<service>:1.0.0
# Registry: localhost:5000 (for local K8s), ghcr.io/<org>/ for CI

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$PLATFORM_DIR/backend"
IMAGE_REGISTRY="${IMAGE_REGISTRY:-localhost:5000/omobio}"
VERSION="${VERSION:-1.0.0}"
NO_CACHE="${1:-}"

SERVICES=(
  api-gateway
  config-tenant-service
  customer-identity-service
  admin-identity-service
  account-entitlement-service
  dashboard-bff
  product-service
  usage-service
  billing-service
  payment-service
  notification-service
  content-service
  journey-service
  reporting-service
  ai-gateway
  audit-service
  insurance-service
  approval-service
)

echo "=============================================="
echo " OMOBIO Selfcare Platform — Docker Build"
echo " Registry : $IMAGE_REGISTRY"
echo " Version  : $VERSION"
echo " Services : ${#SERVICES[@]}"
echo "=============================================="

# Build each service
for SERVICE in "${SERVICES[@]}"; do
  DOCKERFILE="$BACKEND_DIR/$SERVICE/Dockerfile"
  if [ ! -f "$DOCKERFILE" ]; then
    echo "⚠️  Dockerfile not found: $DOCKERFILE — skipping $SERVICE"
    continue
  fi

  IMAGE="$IMAGE_REGISTRY/$SERVICE:$VERSION"
  echo ""
  echo "▶▶ Building $SERVICE → $IMAGE"

  # Build context = PLATFORM root (not just backend/)
  # Required because the Dockerfile references ../industry-packs
  # We pass -f for the Dockerfile and the platform root as context.
  if [ -n "$NO_CACHE" ]; then
    docker build \
      --no-cache \
      --build-arg "MODULE=$SERVICE" \
      -t "$IMAGE" \
      -f "$DOCKERFILE" \
      "$PLATFORM_DIR"
  else
    docker build \
      --build-arg "MODULE=$SERVICE" \
      -t "$IMAGE" \
      -f "$DOCKERFILE" \
      "$PLATFORM_DIR"
  fi

  echo "✓ $SERVICE built"
done

# Tag latest
echo ""
echo "▶▶ Tagging latest versions"
for SERVICE in "${SERVICES[@]}"; do
  docker tag "$IMAGE_REGISTRY/$SERVICE:$VERSION" "$IMAGE_REGISTRY/$SERVICE:latest" 2>/dev/null || true
done

echo ""
echo "=============================================="
echo "✓ All images built successfully"
echo ""
echo " Images:"
for SERVICE in "${SERVICES[@]}"; do
  SIZE=$(docker image inspect "$IMAGE_REGISTRY/$SERVICE:$VERSION" --format='{{.Size}}' 2>/dev/null | numfmt --to=iec 2>/dev/null || echo "?")
  echo "   $IMAGE_REGISTRY/$SERVICE:$VERSION  ($SIZE)"
done
echo ""
echo " To deploy with docker-compose:"
echo "   docker compose up -d"
echo ""
echo " To deploy with kubectl:"
echo "   kubectl apply -f deploy/kubernetes/"
echo "=============================================="
