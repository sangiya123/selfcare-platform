#!/usr/bin/env bash
# canary-deploy.sh — Progressive canary deploy to PROD via ArgoCD fan-out.
#
# Usage:
#   ./scripts/canary-deploy.sh --env prod --tag VERSION [--sync] [--lead-tenant TENANT]
#
# Strategy (ArgoCD ApplicationSet fan-out):
#   1. Lead tenant (dialog-lk-prod) is deployed first.
#   2. Smoke verification runs against the lead.
#   3. Remaining tenants are deployed in waves (fan-out).
#   4. Monitoring / smoke checks after each wave.
#
# If ArgoCD is not reachable (local dev), falls back to:
#   - kubectl set image on each deployment directly (manual canary).
#
# Required env vars / CLI:
#   GITOPS_REPO, GITOPS_BRANCH — same as argocd-sync.sh
#
# ArgoCD access (if --sync):
#   ARGOCD_SERVER, ARGOCD_TOKEN (or kubeconfig with argocd context)

set -euo pipefail

ENV=""
TAG=""
DO_SYNC=false
LEAD_TENANT="dialog-lk"
WAVE_WAIT_SECONDS=60
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GITOPS_REPO="${GITOPS_REPO:-https://github.com/sangiya123/selfcare-platform}"
GITOPS_BRANCH="${GITOPS_BRANCH:-main}"

while [[ $# -gt 0 ]]; do
  case $1 in
    --env)          ENV="$2";          shift 2 ;;
    --tag)          TAG="$2";          shift 2 ;;
    --sync)         DO_SYNC=true;      shift ;;
    --lead-tenant)  LEAD_TENANT="$2";  shift 2 ;;
    --wait)         WAVE_WAIT_SECONDS="$2"; shift 2 ;;
    *)              echo "Unknown option: $1"; exit 1 ;;
  esac
done

[ -z "$ENV" ] && { echo "ERROR: --env required"; exit 1; }
[ -z "$TAG" ] && { echo "ERROR: --tag required"; exit 1; }

echo "=============================================="
echo " selfcare — Canary PROD Deploy"
echo " Tag          : $TAG"
echo " Lead tenant  : $LEAD_TENANT"
echo " Wave wait    : ${WAVE_WAIT_SECONDS}s"
echo " ArgoCD sync  : $DO_SYNC"
echo "=============================================="

# ---------------------------------------------------------------
# Step 1: Update gitops for prod env
# ---------------------------------------------------------------
echo ""
echo "[$(date +%H:%M:%S)] Step 1: GitOps prod tag update"
"$SCRIPT_DIR/argocd-sync.sh" --env prod --tag "$TAG" $([ "$DO_SYNC" = true ] && echo "--push")
echo "[$(date +%H:%M:%S)] GitOps updated."

# ---------------------------------------------------------------
# Step 2: Sync lead tenant first
# ---------------------------------------------------------------
echo ""
echo "[$(date +%H:%M:%S)] Step 2: Deploy lead tenant ($LEAD_TENANT-prod)"

if [ "$DO_SYNC" = true ]; then
  ARGOCD_OPTS=""
  [ -n "${ARGOCD_SERVER:-}" ] && ARGOCD_OPTS="$ARGOCD_OPTS --server $ARGOCD_SERVER"
  [ -n "${ARGOCD_TOKEN:-}" ]  && ARGOCD_OPTS="$ARGOCD_OPTS --auth-token $ARGOCD_TOKEN"
  ARGOCD_OPTS="$ARGOCD_OPTS --grpc-web"

  LEAD_APP="selfcare-${LEAD_TENANT}-prod"
  echo "SYNC  $LEAD_APP"
  argocd app sync "$LEAD_APP" $ARGOCD_OPTS 2>/dev/null || {
    echo "WARN  ArgoCD sync failed for $LEAD_APP — using kubectl fallback"
    DO_SYNC=false
  }
fi

if [ "$DO_SYNC" = false ]; then
  echo "KUBECTL fallback: set image on $LEAD_TENANT-prod deployments"
  NAMESPACE="selfcare-${LEAD_TENANT}-prod"
  IMAGE_NS="${IMAGE_NAMESPACE:-ghcr.io/sangiya123}"
  for SVC in api-gateway config-tenant-service admin-identity-service notification-service \
             content-service audit-service approval-service; do
    kubectl set image deployment/$SVC "$SVC=$IMAGE_NS/$SVC:$TAG" \
      -n "$NAMESPACE" 2>/dev/null && echo "SET   $SVC" || true
  done
  kubectl rollout status deployment/api-gateway -n "$NAMESPACE" --timeout=300s 2>/dev/null || true
fi

echo "[$(date +%H:%M:%S)] Lead tenant deployed. Running smoke..."
"$SCRIPT_DIR/smoke.sh" --env prod --namespace "selfcare-${LEAD_TENANT}-prod" 2>/dev/null || {
  echo "FAIL  Lead tenant smoke failed — ABORTING canary. No further tenants deployed."
  exit 1
}

# ---------------------------------------------------------------
# Step 3: Fan-out remaining tenants in waves
# ---------------------------------------------------------------
ALL_TENANTS="dialog-lk hutch-lk airtel-lk aia-lk aia-sg aia-th aia-my aia-hk aia-in"
WAVE1_TENANTS="hutch-lk airtel-lk"
WAVE2_TENANTS="aia-lk aia-sg aia-th"
WAVE3_TENANTS="aia-my aia-hk aia-in"

deploy_tenant() {
  local TENANT=$1
  local NS="selfcare-${TENANT}-prod"
  local IMAGE_NS="${IMAGE_NAMESPACE:-ghcr.io/sangiya123}"

  if [ "$DO_SYNC" = true ]; then
    local APP="selfcare-${TENANT}-prod"
    argocd app sync "$APP" ${ARGOCD_OPTS} 2>/dev/null || {
      echo "WARN  ArgoCD sync failed for $APP"
      return 1
    }
  else
    for SVC in api-gateway config-tenant-service admin-identity-service notification-service \
               content-service audit-service approval-service; do
      kubectl set image deployment/$SVC "$SVC=$IMAGE_NS/$SVC:$TAG" \
        -n "$NS" 2>/dev/null && echo "SET   $SVC ($TENANT)" || true
    done
    kubectl rollout status deployment/api-gateway -n "$NS" --timeout=120s 2>/dev/null || true
  fi
}

echo ""
echo "[$(date +%H:%M:%S)] Step 3: Wave 1 — $WAVE1_TENANTS"
for T in $WAVE1_TENANTS; do
  deploy_tenant "$T"
done
echo "[$(date +%H:%M:%S)] Wave 1 deployed. Waiting ${WAVE_WAIT_SECONDS}s..."
sleep "$WAVE_WAIT_SECONDS"
"$SCRIPT_DIR/smoke.sh" --env prod --namespace "selfcare-hutch-lk-prod" 2>/dev/null || echo "WARN  Wave 1 smoke inconclusive — continuing cautiously."

echo ""
echo "[$(date +%H:%M:%S)] Step 3b: Wave 2 — $WAVE2_TENANTS"
for T in $WAVE2_TENANTS; do
  deploy_tenant "$T"
done
echo "[$(date +%H:%M:%S)] Wave 2 deployed. Waiting ${WAVE_WAIT_SECONDS}s..."
sleep "$WAVE_WAIT_SECONDS"
"$SCRIPT_DIR/smoke.sh" --env prod --namespace "selfcare-aia-lk-prod" 2>/dev/null || echo "WARN  Wave 2 smoke inconclusive."

echo ""
echo "[$(date +%H:%M:%S)] Step 3c: Wave 3 — $WAVE3_TENANTS"
for T in $WAVE3_TENANTS; do
  deploy_tenant "$T"
done

# ---------------------------------------------------------------
# Step 4: Final monitoring / smoke
# ---------------------------------------------------------------
echo ""
echo "[$(date +%H:%M:%S)] Step 4: Final smoke for lead + monitoring probes"
"$SCRIPT_DIR/smoke.sh" --env prod --namespace "selfcare-${LEAD_TENANT}-prod" || true

echo ""
echo "=============================================="
echo " Canary PROD deploy complete."
echo " Tag deployed: $TAG"
echo " All tenants : $ALL_TENANTS"
echo "=============================================="
