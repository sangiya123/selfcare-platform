#!/usr/bin/env bash
# build-images.sh — Build and optionally push all selfcare microservice Docker images.
#
# Usage:
#   ./scripts/build-images.sh [--push] [--no-cache] [--tag VERSION] [--registry REGISTRY]
#
# Examples:
#   ./scripts/build-images.sh                          # local build, no push
#   ./scripts/build-images.sh --push                   # build + push (for CI)
#   ./scripts/build-images.sh --push --tag rc-142      # explicit tag override
#
# Environment overrides:
#   IMAGE_NAMESPACE  (default: ghcr.io/sangiya123)   Registry/org prefix
#   VERSION          (default: 1.0.0)              Image tag
#
# Prerequisites:
#   - Docker images already compiled by Maven:  backend/.image-context-<service>/
#   - Maven built jars placed in those dirs by the CI checkout / Jenkins pipeline.
#
# Tags produced per service:
#   <registry>/<service>:<tag>
#   <registry>/<service>:latest   (always added as alias)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$PLATFORM_DIR/backend"
IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-ghcr.io/sangiya123}"
VERSION="${VERSION:-1.0.0}"
PUSH_IMAGES=false
NO_CACHE=""
EXPLICIT_TAG=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --push)      PUSH_IMAGES=true; shift ;;
    --no-cache)  NO_CACHE="--no-cache"; shift ;;
    --tag)       EXPLICIT_TAG="$2"; shift 2 ;;
    --registry)  IMAGE_NAMESPACE="$2"; shift 2 ;;
    *)           echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [ -n "$EXPLICIT_TAG" ]; then
  VERSION="$EXPLICIT_TAG"
fi

SERVICES=(
  api-gateway
  config-tenant-service
  customer-identity-service
  admin-identity-service
  account-entitlement-service
  dashboard-bff
  product-service
  usage-service
  support-service
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
echo " Selfcare — Docker Build"
echo " Registry : $IMAGE_NAMESPACE"
echo " Version  : $VERSION"
echo " Push     : $PUSH_IMAGES"
echo " Services : ${#SERVICES[@]}"
echo "=============================================="

BUILT=0
FAILED=0

for SERVICE in "${SERVICES[@]}"; do
  CONTEXT="$BACKEND_DIR/.image-context-${SERVICE}"

  if [ ! -d "$CONTEXT" ] || [ ! -f "$CONTEXT/Dockerfile" ]; then
    echo "SKIP  $SERVICE — no .image-context-$SERVICE/ dir or Dockerfile"
    FAILED=$((FAILED + 1))
    continue
  fi

  if [ ! -f "$CONTEXT/app.jar" ]; then
    echo "SKIP  $SERVICE — no app.jar in .image-context-$SERVICE/"
    FAILED=$((FAILED + 1))
    continue
  fi

  IMAGE="$IMAGE_NAMESPACE/$SERVICE:$VERSION"
  LATEST="$IMAGE_NAMESPACE/$SERVICE:latest"

  echo ""
  echo "BUILD $SERVICE → $IMAGE"

  docker build $NO_CACHE \
    --file "$CONTEXT/Dockerfile" \
    --tag "$IMAGE" \
    "$CONTEXT"

  docker tag "$IMAGE" "$LATEST"
  echo "OK    $SERVICE ($IMAGE)"

  if [ "$PUSH_IMAGES" = true ]; then
    echo "PUSH  $IMAGE"
    docker push "$IMAGE"
    echo "PUSH  $LATEST"
    docker push "$LATEST"
  fi

  BUILT=$((BUILT + 1))
done

echo ""
echo "=============================================="
echo " Done. Built: $BUILT | Skipped: $FAILED"
if [ "$PUSH_IMAGES" = true ]; then
  echo " Images pushed to: $IMAGE_NAMESPACE"
fi
echo "=============================================="
