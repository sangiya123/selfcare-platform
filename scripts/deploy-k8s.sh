#!/usr/bin/env bash
# deploy-k8s.sh — Deploy selfcare microservices to Kubernetes ONE BY ONE.
#
# Deployment model (v3):
#   - Stateful infrastructure (MongoDB/Redis/MySQL/Kafka/Mongo-Express/PMA)
#     lives OUTSIDE Kubernetes (docker-compose on the host / managed PaaS).
#   - Kubernetes runs ONLY the stateless workloads: the 19 backend
#     microservices + the admin portal, deployed sequentially one-by-one
#     via the umbrella Helm chart using deploy.isolated=true.
#
# Usage:
#   ./scripts/deploy-k8s.sh --env dev [--tag VERSION] [--registry REG] [--namespace NS]
#                            [--context CTX] [--local] [--service NAME] [--skip-seed]
#
# Examples:
#   # Deploy ALL microservices one-by-one to dev (EKS)
#   ./scripts/deploy-k8s.sh --env dev --tag dev-42 --registry ghcr.io/sangiya123
#
#   # Deploy ONLY one service (true one-by-one, used by Jenkins loops)
#   ./scripts/deploy-k8s.sh --env dev --tag dev-42 --registry ghcr.io/sangiya123 --service config-tenant-service
#
#   # Local docker-desktop (no registry, table-driven service list)
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
HELM_CHART="$PLATFORM_DIR/backend/deploy/helm"
ADMIN_CHART="$PLATFORM_DIR/admin/selfcare-admin/deploy/helm"
KUBECTL="kubectl"

# --- All 19 microservices (source of truth: backend/deploy/helm/values.yaml) ---
SERVICES=(
  api-gateway config-tenant-service customer-identity-service admin-identity-service
  account-entitlement-service dashboard-bff product-service usage-service support-service
  billing-service payment-service notification-service content-service journey-service
  reporting-service ai-gateway audit-service insurance-service approval-service
)

# --- Defaults ---
ENV=""
VERSION="${VERSION:-latest}"
IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-ghcr.io/sangiya123}"
SELFCARE_CONTEXT="${SELFCARE_CONTEXT:-}"
SELFCARE_NAMESPACE="${SELFCARE_NAMESPACE:-}"
LOCAL_MODE=false
SKIP_SEED=false
ONLY_SERVICE=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --env)            ENV="$2";                shift 2 ;;
    --tag)            VERSION="$2";            shift 2 ;;
    --registry)       IMAGE_NAMESPACE="$2";    shift 2 ;;
    --namespace)      SELFCARE_NAMESPACE="$2";   shift 2 ;;
    --context)        SELFCARE_CONTEXT="$2";     shift 2 ;;
    --local)          LOCAL_MODE=true;          shift ;;
    --skip-seed)      SKIP_SEED=true;           shift ;;
    --service)        ONLY_SERVICE="$2";         shift 2 ;;
    *)                echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [ -z "$ENV" ]; then
  echo "ERROR: --env is required (dev | stg | reg | prod)"
  exit 1
fi

# Derive namespace: selfcare-dev / selfcare-stg / selfcare-reg / selfcare-prod
if [ -z "$SELFCARE_NAMESPACE" ]; then
  SELFCARE_NAMESPACE="selfcare-${ENV}"
fi

# Resolve kubectl context
if [ -n "$SELFCARE_CONTEXT" ]; then
  KUBECTL="$KUBECTL --context $SELFCARE_CONTEXT"
elif [ "$LOCAL_MODE" = true ]; then
  KUBECTL="$KUBECTL --context docker-desktop"
fi

echo "=============================================="
echo " selfcare — Kubernetes Deploy (one-by-one)"
echo " Env       : $ENV"
echo " Namespace : $SELFCARE_NAMESPACE"
echo " Tag       : $VERSION"
echo " Registry  : $IMAGE_NAMESPACE"
echo " Context   : $(kubectl config current-context 2>/dev/null || echo auto)"
echo " Local     : $LOCAL_MODE"
echo " Service   : ${ONLY_SERVICE:-ALL (${#SERVICES[@]})}"
echo "=============================================="

# Step 1: Namespace
echo ""
echo "--- Namespace $SELFCARE_NAMESPACE ---"
$KUBECTL create namespace "$SELFCARE_NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
echo "OK    namespace"

# Step 2: Secrets (registry pull secret + infra creds)
echo "--- Secrets ---"
if [ -f "$K8S_DIR/secrets.yaml" ]; then
  $KUBECTL apply -f "$K8S_DIR/secrets.yaml" -n "$SELFCARE_NAMESPACE" || true
fi
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

# Step 3: Shared ConfigMaps (behavior + per-service config comes from Helm)
echo "--- ConfigMaps ---"
if [ -f "$K8S_DIR/configmap.yaml" ]; then
  $KUBECTL apply -f "$K8S_DIR/configmap.yaml" -n "$SELFCARE_NAMESPACE"
fi
echo "OK    configmaps"

# Step 4: Tenant seed (once per namespace, before services come up)
if [ "$SKIP_SEED" = false ]; then
  echo "--- Tenant seed ---"
  if [ -f "$K8S_DIR/tenant-seeding-job.yaml" ]; then
    $KUBECTL apply -f "$K8S_DIR/tenant-seeding-job.yaml" -n "$SELFCARE_NAMESPACE" || true
    $KUBECTL wait --for=condition=complete --timeout=60s \
      job/selfcare-tenant-seed-dialog -n "$SELFCARE_NAMESPACE" 2>/dev/null || true
    echo "OK    tenant seed"
  else
    echo "SKIP  no tenant-seeding-job.yaml (seed via docker-compose mongo-init instead)"
  fi
else
  echo "--- Tenant seed: skipped ---"
fi

# Step 5: Deploy microservices ONE BY ONE via umbrella Helm chart (isolated mode)
deploy_one() {
  local service="$1"
  echo ""
  echo "--- Deploy $service ---"
  helm upgrade --install "selfcare-${service}" "$HELM_CHART" \
    --namespace "$SELFCARE_NAMESPACE" --create-namespace \
    --set deploy.isolated=true \
    --set deploy.service="$service" \
    --set deploy.image.repository="$IMAGE_NAMESPACE/$service" \
    --set deploy.image.tag="$VERSION" \
    --set environment.name="$ENV" \
    --set image.tag="$VERSION" \
    --wait --timeout 5m
  echo "OK    $service ready"
}

if [ -n "$ONLY_SERVICE" ]; then
  deploy_one "$ONLY_SERVICE"
else
  for service in "${SERVICES[@]}"; do
    deploy_one "$service"
  done
fi

# Step 6: Admin portal (nginx/React bundle) — stateless, deploy last
echo ""
echo "--- Deploy admin portal ---"
helm upgrade --install selfcare-admin-portal "$ADMIN_CHART" \
  --namespace "$SELFCARE_NAMESPACE" --create-namespace \
  --set image.repository="$IMAGE_NAMESPACE/selfcare-admin" \
  --set image.tag="$VERSION" \
  --wait --timeout 3m
echo "OK    admin portal ready"

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