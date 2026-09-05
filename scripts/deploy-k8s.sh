#!/usr/bin/env bash
# deploy-k8s.sh — Deploy OMOBIO to Docker Desktop Kubernetes
# Usage: ./scripts/deploy-k8s.sh [--build] [--skip-build]
#
# Prerequisites:
#   - Docker Desktop with Kubernetes enabled
#     (Settings → Kubernetes → Enable Kubernetes → Apply & Restart)
#   - kubectl connected to docker-desktop context
#   - Docker images built locally via build-images.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
K8S_DIR="$PLATFORM_DIR/deploy/kubernetes"

echo "=============================================="
echo " OMOBIO Selfcare Platform — K8s Deploy"
echo "=============================================="

# Step 1: Build images
if [ "${1:-}" = "--build" ]; then
  echo ""
  echo "▶ Building Docker images..."
  "$SCRIPT_DIR/build-images.sh"
fi

# Step 2: Switch kubectl context to docker-desktop
echo ""
echo "▶ Setting kubectl context to docker-desktop..."
if ! kubectl config get-contexts docker-desktop &>/dev/null; then
  echo "ERROR: docker-desktop context not found."
  echo "Make sure Kubernetes is enabled in Docker Desktop settings."
  echo "Then run: kubectl config use-context docker-desktop"
  exit 1
fi
kubectl config use-context docker-desktop 2>/dev/null || true

# Step 3: Apply namespace
echo ""
echo "▶ Creating namespace..."
kubectl apply -f "$K8S_DIR/namespace.yaml"
echo "✓ Namespace 'omobio' created"

# Step 4: Apply secrets
echo ""
echo "▶ Applying secrets..."
kubectl apply -f "$K8S_DIR/secrets.yaml"
echo "✓ Secrets applied"

# Step 5: Apply config maps
echo ""
echo "▶ Applying config maps..."
kubectl apply -f "$K8S_DIR/configmap.yaml"
echo "✓ ConfigMaps applied"

# Step 6: Deploy infrastructure
echo ""
echo "▶ Deploying infrastructure (MongoDB, Redis, Kafka, MySQL)..."
kubectl apply -f "$K8S_DIR/infra.yaml"
echo "✓ Infrastructure deployed"

# Step 7: Wait for infrastructure
echo ""
echo "▶ Waiting for MongoDB to be ready..."
kubectl wait --for=condition=available --timeout=120s deployment/mongodb -n omobio 2>/dev/null || \
  kubectl rollout status deployment/mongodb -n omobio --timeout=120s
echo "✓ MongoDB ready"

echo "▶ Waiting for MySQL to be ready..."
kubectl wait --for=condition=available --timeout=120s deployment/mysql -n omobio 2>/dev/null || \
  kubectl rollout status deployment/mysql -n omobio --timeout=120s
echo "✓ MySQL ready"

# Step 8: Seed tenant data
echo ""
echo "▶ Seeding dialog-lk tenant config..."
kubectl apply -f "$K8S_DIR/tenant-seeding-job.yaml"
# Wait for seeding job to complete
kubectl wait --for=condition=complete --timeout=60s job/omobio-tenant-seed-dialog -n omobio 2>/dev/null || true
SEED_LOGS=$(kubectl logs job/omobio-tenant-seed-dialog -n omobio 2>/dev/null || echo "")
if echo "$SEED_LOGS" | grep -q "seeding complete\|Seeded"; then
  echo "✓ Tenant dialog-lk seeded"
else
  echo "⚠️  Tenant seeding may not have completed — check: kubectl logs job/omobio-tenant-seed-dialog -n omobio"
fi

# Step 9: Deploy microservices
echo ""
echo "▶ Deploying OMOBIO microservices..."
kubectl apply -f "$K8S_DIR/services.yaml"
echo "✓ Microservices deployed"

# Step 10: Wait for API Gateway
echo ""
echo "▶ Waiting for API Gateway to be ready..."
kubectl rollout status deployment/api-gateway -n omobio --timeout=180s
echo "✓ API Gateway ready"

# Step 11: Check all pods
echo ""
echo "▶ Checking pod status..."
kubectl get pods -n omobio

echo ""
echo "=============================================="
echo "✓ OMOBIO deployed to Docker Desktop Kubernetes"
echo ""
echo " Access points (via NodePort):"
echo "   API Gateway   http://localhost:30080"
echo "   Prometheus    http://localhost:30090"
echo "   Grafana      http://localhost:30300  (admin/admin)"
echo ""
echo " All services:"
kubectl get svc -n omobio
echo ""
echo " Pods:"
kubectl get pods -n omobio --no-headers | wc -l | xargs echo " Total pods:"
echo ""
echo " Tear down:"
echo "   kubectl delete -f $K8S_DIR/"
echo "=============================================="
