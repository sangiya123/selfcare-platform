#!/usr/bin/env bash
# deploy-k8s.sh — Deploy selfcare to Kubernetes (EKS or docker-desktop).
#
# Usage:
#   ./scripts/deploy-k8s.sh --env dev [--tag VERSION] [--registry REG] [--namespace NS]
#                            [--context CTX] [--skip-seed] [--local]
#
# Examples:
#   # EKS dev deploy (from Jenkins)
#   ./scripts/deploy-k8s.sh --env dev --tag dev-42 --registry ghcr.io/sangiya123
#
#   # Local docker-desktop deploy (default namespace = selfcare, no registry)
#   ./scripts/deploy-k8s.sh --env dev --local
#
# Environment overrides:
#   IMAGE_NAMESPACE   Registry prefix (default: ghcr.io/sangiya123)
#   VERSION           Image tag (default: latest)
#   SELFCARE_CONTEXT    kubectl context (default: auto-detect)
#   SELFCARE_NAMESPACE  Namespace override (default: selfcare-$ENV)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
K8S_DIR="$PLATFORM_DIR/deploy/kubernetes"
KUBECTL="kubectl"

# --- Defaults ---
ENV=""
VERSION="${VERSION:-latest}"
IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-ghcr.io/sangiya123}"
SELFCARE_CONTEXT="${SELFCARE_CONTEXT:-}"
SELFCARE_NAMESPACE="${SELFCARE_NAMESPACE:-}"
LOCAL_MODE=false
SKIP_SEED=false
SKIP_INFRA=false
CHECK_ONLY=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --env)            ENV="$2";                shift 2 ;;
    --tag)            VERSION="$2";            shift 2 ;;
    --registry)       IMAGE_NAMESPACE="$2";    shift 2 ;;
    --namespace)      SELFCARE_NAMESPACE="$2";   shift 2 ;;
    --context)        SELFCARE_CONTEXT="$2";     shift 2 ;;
    --local)          LOCAL_MODE=true;          shift ;;
    --skip-seed)      SKIP_SEED=true;           shift ;;
    --skip-infra)     SKIP_INFRA=true;          shift ;;
    --check-only)     CHECK_ONLY=true;          shift ;;
    *)                echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [ -z "$ENV" ]; then
  echo "ERROR: --env is required (dev | stg | prod)"
  exit 1
fi

# Derive namespace: selfcare-dev / selfcare-stg / selfcare-prod (or selfcare in local mode)
if [ -z "$SELFCARE_NAMESPACE" ]; then
  if [ "$LOCAL_MODE" = true ]; then
    SELFCARE_NAMESPACE="selfcare"
  else
    SELFCARE_NAMESPACE="selfcare-${ENV}"
  fi
fi

# Resolve kubectl context
if [ -n "$SELFCARE_CONTEXT" ]; then
  KUBECTL="$KUBECTL --context $SELFCARE_CONTEXT"
elif [ "$LOCAL_MODE" = true ]; then
  KUBECTL="$KUBECTL --context docker-desktop"
fi

echo "=============================================="
echo " selfcare — Kubernetes Deploy"
echo " Env       : $ENV"
echo " Namespace : $SELFCARE_NAMESPACE"
echo " Tag       : $VERSION"
echo " Registry  : $IMAGE_NAMESPACE"
echo " Context   : $(kubectl config current-context 2>/dev/null || echo auto)"
echo " Local     : $LOCAL_MODE"
echo "=============================================="

# Check-only mode: verify rollout health
if [ "$CHECK_ONLY" = true ]; then
  echo ""
  echo "--- Checking rollout status for namespace $SELFCARE_NAMESPACE ---"
  for SERVICE in api-gateway config-tenant-service customer-identity-service admin-identity-service \
                 account-entitlement-service dashboard-bff product-service usage-service billing-service \
                 payment-service notification-service content-service journey-service reporting-service \
                 ai-gateway audit-service insurance-service approval-service; do
    $KUBECTL rollout status deployment/$SERVICE -n $SELFCARE_NAMESPACE --timeout=5s 2>/dev/null \
      && echo "OK    $SERVICE" \
      || echo "FAIL  $SERVICE"
  done
  exit 0
fi

# Step 1: Create / patch namespace
echo ""
echo "--- Namespace $SELFCARE_NAMESPACE ---"
$KUBECTL create namespace "$SELFCARE_NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
echo "OK    namespace"

# Step 2: Secrets (registry pull secret + infra creds)
echo "--- Secrets ---"
if [ -f "$K8S_DIR/secrets.yaml" ]; then
  $KUBECTL apply -f "$K8S_DIR/secrets.yaml" -n "$SELFCARE_NAMESPACE" || true
fi

# If EKS and IMAGE_NAMESPACE contains registry host, create pull secret
REGISTRY_HOST=$(echo "$IMAGE_NAMESPACE" | cut -d/ -f1)
if [ "$LOCAL_MODE" = false ] && [ -n "$REGISTRY_HOST" ]; then
  $KUBECTL create secret docker-registry selfcare-registry-secret \
    --docker-server="$REGISTRY_HOST" \
    --docker-username="${DOCKER_USERNAME:-}" \
    --docker-password="${DOCKER_PASSWORD:-}" \
    -n "$SELFCARE_NAMESPACE" --dry-run=client -o yaml 2>/dev/null \
    | $KUBECTL apply -f - 2>/dev/null || true
fi
echo "OK    secrets"

# Step 3: ConfigMaps
echo "--- ConfigMaps ---"
if [ -f "$K8S_DIR/configmap.yaml" ]; then
  $KUBECTL apply -f "$K8S_DIR/configmap.yaml" -n "$SELFCARE_NAMESPACE"
fi
echo "OK    configmaps"

# Step 4: Infrastructure (MongoDB/MySQL/Redis/Kafka)
if [ "$SKIP_INFRA" = false ]; then
  echo "--- Infrastructure (Mongo/MySQL/Redis/Kafka) ---"
  if [ -f "$K8S_DIR/infra.yaml" ]; then
    $KUBECTL apply -f "$K8S_DIR/infra.yaml" -n "$SELFCARE_NAMESPACE"
    # Wait for Mongo
    echo "Waiting for MongoDB..."
    $KUBECTL rollout status deployment/mongodb -n "$SELFCARE_NAMESPACE" --timeout=120s 2>/dev/null || true
    echo "Waiting for MySQL..."
    $KUBECTL rollout status deployment/mysql -n "$SELFCARE_NAMESPACE" --timeout=120s 2>/dev/null || true
    echo "Waiting for Redis..."
    $KUBECTL rollout status deployment/redis -n "$SELFCARE_NAMESPACE" --timeout=120s 2>/dev/null || true
    echo "OK    infrastructure ready"
  fi
else
  echo "--- Infrastructure: skipped (--skip-infra) ---"
fi

# Step 5: Seed tenant data (once per namespace)
if [ "$SKIP_SEED" = false ]; then
  echo "--- Tenant seed ---"
  if [ -f "$K8S_DIR/tenant-seeding-job.yaml" ]; then
    $KUBECTL apply -f "$K8S_DIR/tenant-seeding-job.yaml" -n "$SELFCARE_NAMESPACE" || true
    $KUBECTL wait --for=condition=complete --timeout=60s \
      job/selfcare-tenant-seed-dialog -n "$SELFCARE_NAMESPACE" 2>/dev/null || true
    echo "OK    tenant seed"
  fi
else
  echo "--- Tenant seed: skipped ---"
fi

# Step 6: Deploy microservices
echo ""
echo "--- Deploy microservices ---"
if [ -f "$K8S_DIR/services.yaml" ]; then
  $KUBECTL apply -f "$K8S_DIR/services.yaml" -n "$SELFCARE_NAMESPACE"
fi

# Step 7: Set image tag on each deployment
echo "--- Set images to $IMAGE_NAMESPACE/*:$VERSION ---"
for SERVICE in api-gateway config-tenant-service customer-identity-service admin-identity-service \
               account-entitlement-service dashboard-bff product-service usage-service billing-service \
               payment-service notification-service content-service journey-service reporting-service \
               ai-gateway audit-service insurance-service approval-service; do
  IMAGE_REF="$IMAGE_NAMESPACE/$SERVICE:$VERSION"
  $KUBECTL set image deployment/$SERVICE "$SERVICE=$IMAGE_REF" -n "$SELFCARE_NAMESPACE" 2>/dev/null \
    && echo "SET   $SERVICE → $IMAGE_REF" \
    || echo "SKIP  $SERVICE (no existing deployment)"
done

# Step 8: Rollout status
echo ""
echo "--- Rollout status ---"
for SERVICE in api-gateway config-tenant-service customer-identity-service admin-identity-service \
               account-entitlement-service dashboard-bff product-service usage-service billing-service \
               payment-service notification-service content-service journey-service reporting-service \
               ai-gateway audit-service insurance-service approval-service; do
  $KUBECTL rollout status deployment/$SERVICE -n "$SELFCARE_NAMESPACE" --timeout=300s 2>/dev/null \
    && echo "OK    $SERVICE ready" \
    || echo "WARN  $SERVICE timeout"
done

echo ""
echo "=============================================="
echo " selfcare deployed — env=$ENV namespace=$SELFCARE_NAMESPACE"
echo ""
echo " Pods:"
$KUBECTL get pods -n "$SELFCARE_NAMESPACE" -o wide
echo ""
echo " Services:"
$KUBECTL get svc -n "$SELFCARE_NAMESPACE"
echo "=============================================="
