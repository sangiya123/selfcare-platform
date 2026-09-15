#!/usr/bin/env bash
# start-local.sh — Start the full selfcare platform via docker-compose
# Usage: ./scripts/start-local.sh [--build] [--skip-build]
#
# This starts:
#   Infrastructure: MongoDB, Redis, Kafka, MySQL, Zookeeper
#   Services:      All 19 selfcare microservices
#   Observability:  Prometheus, Grafana
#
# Access points:
#   API Gateway:     http://localhost:8080
#   Grafana:         http://localhost:3000  (admin/Selfcare_Gr4f4n4_Adm1n_Pa55w0rd!2026)
#   Prometheus:       http://localhost:9090
#   MongoDB:          localhost:27017  (selfcare/Selfcare_M0ng0_Db_Pa55w0rd!2026)
#   Kafka:            localhost:9092

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=============================================="
echo " Selfcare Platform — Local Start"
echo "=============================================="

cd "$PLATFORM_DIR"

# Step 1: Build images if requested
if [ "${1:-}" = "--build" ]; then
  echo ""
  echo "▶ Building Docker images..."
  "$SCRIPT_DIR/build-images.sh"
fi

# Step 2: Start infrastructure first
echo ""
echo "▶ Starting infrastructure (MongoDB, Redis, Kafka, MySQL)..."
docker compose up -d mongodb redis kafka zookeeper mysql

# Wait for MongoDB to be ready
echo ""
echo "▶ Waiting for MongoDB to be ready..."
for i in {1..30}; do
  if docker exec selfcare-mongodb mongosh --quiet --eval "db.adminCommand('ping').ok" 2>/dev/null | grep -q "1"; then
    echo "✓ MongoDB ready"
    break
  fi
  echo "  waiting... ($i/30)"
  sleep 2
done

# Step 3: Seed tenant data
echo ""
echo "▶ Seeding dialog-lk tenant config into MongoDB..."
docker exec selfcare-mongodb mongosh --quiet \
  mongodb://selfcare:Selfcare_M0ng0_Db_Pa55w0rd!2026@localhost:27017/selfcare_config?authSource=admin \
  --file /docker-entrypoint-initdb.d/01-tenant-dialog.js \
  2>/dev/null || echo "  (seed script may have already run — continuing)"

# Step 4: Start all services
echo ""
echo "▶ Starting all selfcare microservices..."
docker compose up -d

# Wait for API Gateway to be ready
echo ""
echo "▶ Waiting for API Gateway..."
for i in {1..30}; do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✓ API Gateway ready"
    break
  fi
  echo "  waiting... ($i/30)"
  sleep 5
done

echo ""
echo "=============================================="
echo "✓ Selfcare Platform is running"
echo ""
echo " Endpoints:"
echo "   API Gateway     http://localhost:8080"
echo "   Swagger UI      http://localhost:8080/swagger-ui.html"
echo "   Grafana        http://localhost:3000  (admin/Selfcare_Gr4f4n4_Adm1n_Pa55w0rd!2026)"
echo "   Prometheus     http://localhost:9090"
echo "   Kafka UI       http://localhost:8080/kafka-ui (if enabled)"
echo ""
echo " Docker status:"
docker compose ps
echo ""
echo " Logs:"
echo "   docker compose logs -f api-gateway"
echo ""
echo " Stop:"
echo "   docker compose down"
echo "=============================================="
