#!/usr/bin/env bash
# start-local.sh — Start selfcare infrastructure (docker-compose) + deploy app to Kubernetes
# Usage: ./scripts/start-local.sh [--build] [--skip-build]
#
# This starts:
#   Infrastructure: MongoDB, Mongo Express, Redis, MySQL, PHPMyAdmin, Kafka (docker compose)
#   Application:     Deployed to Kubernetes by the Jenkins pipeline (ci/jenkins/Jenkinsfile).
#                    For a manual local K8s deploy see ./scripts/deploy-k8s.sh --env dev --local
#
# The stateful infra stays local/private. It is never exposed to the public cloud.
#
# Access points:
#   Mongo Express:   http://localhost:8081  (admin / Selfcare_M0ng0Expr3ss_Pa55w0rd!2026)
#   PHPMyAdmin:      http://localhost:8080  (server: mysql, user: selfcare)
#   MongoDB:         localhost:27017  (selfcare/Selfcare_M0ng0_Db_Pa55w0rd!2026)
#   MySQL:           localhost:3306  (selfcare/Selfcare_My5ql_Db_Pa55w0rd!2026)
#   Redis:           localhost:6379  (Selfcare_R3d1s_Pa55w0rd!2026)
#   Kafka:           localhost:9092

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=============================================="
echo " Selfcare Platform — Local Infra Start"
echo "=============================================="

cd "$PLATFORM_DIR"

# Step 1: Start infrastructure
echo ""
echo "▶ Starting infrastructure (MongoDB, Mongo Express, Redis, MySQL, PHPMyAdmin, Kafka)..."
docker compose up -d

# Wait for MongoDB to be ready
echo ""
echo "▶ Waiting for MongoDB to be ready..."
for i in {1..30}; do
  if docker exec selfcare-infra-mongodb mongosh --quiet --eval "db.adminCommand('ping').ok" 2>/dev/null | grep -q "1"; then
    echo "✓ MongoDB ready"
    break
  fi
  echo "  waiting... ($i/30)"
  sleep 2
done

# Step 2: Seed tenant data
echo ""
echo "▶ Seeding dialog-lk tenant config into MongoDB..."
docker exec selfcare-infra-mongodb mongosh --quiet \
  mongodb://selfcare:Selfcare_M0ng0_Db_Pa55w0rd!2026@localhost:27017/selfcare_config?authSource=admin \
  --file /docker-entrypoint-initdb.d/01-tenant-dialog.js \
  2>/dev/null || echo "  (seed script may have already run — continuing)"

# Step 3: infra status
echo ""
echo "▶ Infrastructure status:"
docker compose ps

echo ""
echo "=============================================="
echo "✓ Selfcare infrastructure is running (private, local only)"
echo ""
echo " Stateful infra endpoints:"
echo "   Mongo Express http://localhost:8081  (admin / Selfcare_M0ng0Expr3ss_Pa55w0rd!2026)"
echo "   PHPMyAdmin    http://localhost:8080  (server: mysql, user: selfcare)"
echo ""
echo " Application (microservices + admin portal) deploys to Kubernetes:"
echo "   -> via Jenkins: ci/jenkins/Jenkinsfile (helm chart backend/deploy/helm)"
echo "   -> manual local: ./scripts/deploy-k8s.sh --env dev --local"
echo "   -> portal:       helm upgrade selfcare-admin admin/selfcare-admin/deploy/helm"
echo ""
echo " Container logs:"
echo "   docker compose logs -f kafka"
echo " Stop infra:"
echo "   docker compose down"
echo "=============================================="