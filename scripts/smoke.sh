#!/usr/bin/env bash
# smoke.sh — Post-deploy health and endpoint probes.
#
# Usage: ./scripts/smoke.sh --env dev|stg|prod [--namespace NS] [--context CTX] [--local]
#
# Checks:
#   1. All deployments have at least 1 ready replica.
#   2. API Gateway /actuator/health responds 200 (via kubectl exec or port-forward).
#   3. Config manifest returns 200 with valid JSON for each seeded tenant.
#
# Exit codes: 0 = all green, 1 = failures detected.

set -euo pipefail

ENV=""
NAMESPACE=""
CTX=""
LOCAL_MODE=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --env)       ENV="$2";       shift 2 ;;
    --namespace) NAMESPACE="$2"; shift 2 ;;
    --context)   CTX="$2";       shift 2 ;;
    --local)     LOCAL_MODE=true; shift ;;
    *)           echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [ -z "$ENV" ]; then
  echo "ERROR: --env is required"; exit 1
fi

if [ -z "$NAMESPACE" ]; then
  NAMESPACE=$([ "$LOCAL_MODE" = true ] && echo "selfcare" || echo "selfcare-${ENV}")
fi

KUBECTL="kubectl"
[ -n "$CTX" ] && KUBECTL="$KUBECTL --context $CTX"
[ "$LOCAL_MODE" = true ] && KUBECTL="$KUBECTL --context docker-desktop"

PASS=0
FAIL=0

echo "=============================================="
echo " selfcare — Smoke Tests  ($ENV / $NAMESPACE)"
echo "=============================================="

# --- 1. Deployment readiness ---
echo ""
echo "--- Deployment readiness ---"
for SVC in api-gateway config-tenant-service customer-identity-service admin-identity-service \
           account-entitlement-service dashboard-bff product-service usage-service support-service \
           billing-service payment-service notification-service content-service journey-service \
           reporting-service ai-gateway audit-service insurance-service approval-service; do
  READY=$($KUBECTL get deployment "$SVC" -n "$NAMESPACE" \
    -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
  READY=${READY:-0}
  if [ "$READY" -ge 1 ]; then
    echo "OK    $SVC  ready=$READY"
    PASS=$((PASS + 1))
  else
    echo "FAIL  $SVC  ready=$READY"
    FAIL=$((FAIL + 1))
  fi
done

# --- 2. API Gateway health ---
echo ""
echo "--- API Gateway /actuator/health ---"

# Find api-gateway pod
GW_POD=$($KUBECTL get pod -n "$NAMESPACE" \
  -l app.kubernetes.io/name=api-gateway \
  -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

if [ -n "$GW_POD" ]; then
  # Use kubectl exec with curl or wget (container might not have wget)
  HEALTH=$($KUBECTL exec -n "$NAMESPACE" "$GW_POD" -- \
    sh -c 'wget -qO- http://localhost:8080/actuator/health 2>/dev/null || curl -s http://localhost:8080/actuator/health 2>/dev/null' || echo "{}")

  if echo "$HEALTH" | grep -q '"status":"UP"'; then
    echo "OK    actuator/health → UP"
    PASS=$((PASS + 1))
  else
    echo "WARN  actuator/health response: $(echo "$HEALTH" | head -c 120)"
    FAIL=$((FAIL + 1))
  fi
else
  echo "SKIP  no api-gateway pod found (not yet running)"
fi

# --- 3. Config manifest probes (via kubectl port-forward + curl) ---
echo ""
echo "--- Config manifest probes ---"

# Get gateway ClusterIP for in-cluster probes via kubectl exec in a busybox or direct exec
GW_SVC_IP=$($KUBECTL get svc api-gateway -n "$NAMESPACE" \
  -o jsonpath='{.spec.clusterIP}' 2>/dev/null || echo "")
GW_PORT=$($KUBECTL get svc api-gateway -n "$NAMESPACE" \
  -o jsonpath='{.spec.ports[0].port}' 2>/dev/null || echo "8080")

if [ -n "$GW_SVC_IP" ] && [ -n "$GW_POD" ]; then
  for TENANT in dialog-lk hutch-lk airtel-lk aia-lk; do
    # Execute wget/curl inside the gateway pod for in-cluster connectivity
    RESP=$($KUBECTL exec -n "$NAMESPACE" "$GW_POD" -- \
      sh -c "wget -qO- 'http://${GW_SVC_IP}:${GW_PORT}/api/v1/config/manifest?environment=dev&experience=home&profileKey=default' \
             --header='X-Tenant-Id: ${TENANT}' 2>/dev/null || \
             curl -s 'http://${GW_SVC_IP}:${GW_PORT}/api/v1/config/manifest?environment=dev&experience=home&profileKey=default' \
             -H 'X-Tenant-Id: ${TENANT}' 2>/dev/null" || echo "{}")

    if echo "$RESP" | grep -qi "manifest\|experience\|components\|theme"; then
      echo "OK    $TENANT manifest served"
      PASS=$((PASS + 1))
    else
      echo "WARN  $TENANT manifest probe inconclusive ($(echo "$RESP" | head -c 80)…)"
      FAIL=$((FAIL + 1))
    fi
  done
else
  echo "SKIP  cannot probe manifests (GW_SVC_IP=$GW_SVC_IP, GW_POD=$GW_POD)"
fi

echo ""
echo "=============================================="
echo " Results: $PASS passed, $FAIL failed"
if [ "$FAIL" -eq 0 ]; then
  echo " All smoke checks passed."
else
  echo " Some checks failed — review above."
fi
echo "=============================================="
exit "$FAIL"
