#!/usr/bin/env bash
# verify.sh — Quick smoke test that validates the local selfcare deployment
# Usage: ./scripts/verify.sh [api-gateway-url]
#
# Default URL: http://localhost:8080

set -euo pipefail

URL="${1:-http://localhost:8080}"
TENANT="dialog-lk"

echo "=============================================="
echo " selfcare Smoke Test"
echo " URL    : $URL"
echo " Tenant : $TENANT"
echo "=============================================="

PASS=0
FAIL=0

check() {
  local name="$1"
  local url="$2"
  local expected="$3"

  RESPONSE=$(curl -sf -H "X-Tenant-Id: $TENANT" --max-time 5 "$url" 2>/dev/null || echo "FAIL")
  if echo "$RESPONSE" | grep -q "$expected"; then
    echo "✓ $name"
    PASS=$((PASS+1))
  else
    echo "✗ $name (got: $RESPONSE)"
    FAIL=$((FAIL+1))
  fi
}

# 1. API Gateway
check "API Gateway health"     "$URL/actuator/health"              "UP"
check "API Gateway info"       "$URL/actuator/info"                "name"
check "API Gateway metrics"    "$URL/actuator/prometheus"          "jvm_threads"
check "API Gateway swagger"    "$URL/v3/api-docs"                  "openapi"

# 2. Individual service health (via gateway routing)
for SERVICE in config-tenant-service customer-identity-service account-entitlement-service dashboard-bff product-service usage-service support-service billing-service payment-service notification-service content-service journey-service reporting-service ai-gateway audit-service insurance-service approval-service admin-identity-service; do
  # Each service exposes its own /actuator/health
  PORT=""
  case $SERVICE in
    config-tenant-service) PORT=8081 ;;
    customer-identity-service) PORT=8082 ;;
    admin-identity-service) PORT=8083 ;;
    account-entitlement-service) PORT=8084 ;;
    dashboard-bff) PORT=8085 ;;
    product-service) PORT=8086 ;;
    usage-service) PORT=8087 ;;
    billing-service) PORT=8088 ;;
    payment-service) PORT=8089 ;;
    notification-service) PORT=8090 ;;
    content-service) PORT=8091 ;;
    journey-service) PORT=8092 ;;
    reporting-service) PORT=8093 ;;
    ai-gateway) PORT=8094 ;;
    audit-service) PORT=8095 ;;
    insurance-service) PORT=8096 ;;
    approval-service) PORT=8097 ;;
    support-service) PORT=8098 ;;
  esac
  check "$SERVICE health" "http://localhost:$PORT/actuator/health" "UP"
done

# 3. Verify tenant config in MongoDB
echo ""
echo "=== MongoDB tenant config ==="
docker exec selfcare-mongodb mongosh --quiet \
  mongodb://selfcare:Selfcare_M0ng0_Db_Pa55w0rd!2026@localhost:27017/selfcare_config?authSource=admin \
  --eval "db.tenants.findOne({tenantId: 'dialog-lk'}, {displayName: 1, industry: 1, country: 1, currency: 1})" 2>/dev/null || echo "  (could not query MongoDB)"

echo ""
echo "=============================================="
echo " Results: $PASS passed, $FAIL failed"
echo "=============================================="
exit $FAIL
