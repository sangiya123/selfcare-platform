# OMOBIO Selfcare Platform — Local Deployment

This guide covers running the full OMOBIO platform locally on Docker Desktop.

## Prerequisites

- **Docker Desktop** 4.x+ with Kubernetes enabled
- **kubectl** (bundled with Docker Desktop)
- **Java 25** (only needed if building outside Docker)
- **8 GB RAM** minimum allocated to Docker Desktop
- **10 GB free disk space** for images + volumes

## Quick Start (5 minutes)

### Option A: Docker Compose (recommended first)

```bash
# 1. Build all images and start the platform
./scripts/build-images.sh
./scripts/start-local.sh
```

or on Windows:
```cmd
scripts\build-images.bat
scripts\start-local.bat
```

This starts:
- **Infrastructure**: MongoDB, Redis, Kafka, MySQL, Zookeeper
- **19 microservices** with the dialog-lk tenant seeded
- **Prometheus + Grafana** for monitoring

### Option B: Kubernetes (Docker Desktop)

```bash
# 1. Enable Kubernetes in Docker Desktop first
#    Docker Desktop → Settings → Kubernetes → Enable Kubernetes

# 2. Confirm K8s context
kubectl config use-context docker-desktop

# 3. Build images and deploy
./scripts/build-images.sh
./scripts/deploy-k8s.sh
```

## Access Points

Once running, the platform is accessible at:

| Service | URL | Notes |
|---|---|---|
| **API Gateway** | http://localhost:8080 | All requests enter here |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | Auto-generated API docs |
| **Grafana** | http://localhost:3000 | admin/admin |
| **Prometheus** | http://localhost:9090 | Metrics & alerts |
| **MongoDB** | localhost:27017 | omobio / omobio_pw |
| **MySQL** | localhost:3306 | omobio / omobio_pw |
| **Redis** | localhost:6379 | password: omobio_pw |
| **Kafka** | localhost:9092 | |

For Kubernetes, replace `localhost:8080` with `localhost:30080`, etc.

## Tenant: dialog-lk

The platform is pre-configured for **Dialog Axiata PLC (Sri Lanka)**. Tenant config
is loaded from MongoDB on first boot:

- `tenants/dialog-lk` — tenant metadata
- `themes/dialog-lk-default` — brand colors, fonts, logo
- `layouts/dialog-lk-home` — dashboard widget layout
- `feature_flags/dialog-lk-flags` — feature toggles
- `integrations/dialog-lk-bss` — operator BSS endpoint
- `navigations/dialog-lk-main` — bottom tab bar

To add another tenant (e.g. Hutch, AIA), insert another row in `tenants` and the
corresponding config documents.

## Production Deployment (AWS EKS)

For production deployment to AWS EKS via CI/CD, see:

- `.github/workflows/release-deploy.yml` — full release pipeline
- `backend/deploy/helm/values/` — per-environment Helm values
- `backend/deploy/gitops/applicationset.yaml` — ArgoCD ApplicationSet

The release pipeline:
1. Builds images in CI (GitHub Actions or Jenkins)
2. Signs with cosign (keyless, GitHub OIDC)
3. Generates SLSA L3 provenance
4. Pushes to ECR
5. Updates Helm values + ArgoCD ApplicationSet
6. ArgoCD deploys to EKS (dev → stg → reg → prod)

## Useful Commands

```bash
# View all running services
docker compose ps

# Tail logs
docker compose logs -f api-gateway
docker compose logs -f payment-service

# Restart a single service
docker compose restart api-gateway

# Open a shell in a container
docker exec -it omobio-mongodb mongosh
docker exec -it omobio-mysql mysql -uomobio -pomobio_pw

# Check tenant config
docker exec -it omobio-mongodb mongosh \
  mongodb://omobio:omobio_pw@localhost:27017/omobio_config?authSource=admin \
  --eval "db.tenants.find().pretty()"

# Tear down everything
docker compose down -v

# Kubernetes equivalents
kubectl get pods -n omobio
kubectl logs -n omobio -l app=api-gateway
kubectl port-forward -n omobio svc/api-gateway 8080:8080
```

## Health Checks

Each service exposes Spring Actuator endpoints:

- `/actuator/health` — overall health
- `/actuator/health/liveness` — pod liveness (K8s)
- `/actuator/health/readiness` — pod readiness (K8s)
- `/actuator/prometheus` — Prometheus metrics
- `/actuator/info` — service info
- `/actuator/env` — env vars (PROD: lock down)

## Troubleshooting

### "Cannot connect to MongoDB"
```bash
docker compose logs mongodb
docker exec omobio-mongodb mongosh --eval "db.adminCommand('ping')"
```

### "API Gateway returns 502"
- Check upstream service health: `docker compose ps`
- Check API Gateway logs: `docker compose logs api-gateway`
- Verify tenant header is set: `X-Tenant-Id: dialog-lk`

### "Build fails with Maven errors"
- Ensure you're using Java 25 (`java -version`)
- Check `pom.xml` syntax
- Re-run with `--no-cache`

### "Out of memory in Docker Desktop"
- Increase Docker Desktop memory: Settings → Resources → Memory → 8 GB
