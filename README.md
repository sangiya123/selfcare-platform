# Selfcare Platform — Local Deployment

This guide covers running the selfcare platform locally on Docker Desktop.
The architecture is split: **stateful infrastructure** (MongoDB/Redis/MySQL/
Kafka + admin UIs) runs in Docker Compose; the **19 microservices, admin
portal, Jenkins and SonarQube run on Kubernetes**, deployed one-by-one via
Helm/Jenkins.

## Prerequisites

- **Docker Desktop** 4.x+ with Kubernetes enabled
- **kubectl** (bundled with Docker Desktop)
- **Java 25** (only needed if building outside Docker)
- **8 GB RAM** minimum allocated to Docker Desktop
- **10 GB free disk space** for images + volumes

## Quick Start (5 minutes)

### Option A: Docker Compose (infrastructure only — recommended first)

Stateful infrastructure lives OUTSIDE Kubernetes. Microservices run on K8s
(see Option B) and are deployed ONE BY ONE by the Jenkins pipeline.

```bash
# 1. Start infrastructure (MongoDB, Mongo Express, Redis, MySQL, PHPMyAdmin, Zookeeper, Kafka)
./scripts/start-local.sh
```

or on Windows:
```cmd
scripts\start-local.bat
```

This starts:
- **Infrastructure**: MongoDB, Mongo Express, Redis, MySQL, PHPMyAdmin, Zookeeper, Kafka
- **Admin UIs**: Mongo Express on http://localhost:8081, PHPMyAdmin on http://localhost:8080
- The dialog-lk tenant is seeded on first Mongo boot

> The 19 microservices and the admin portal are NOT here — they run on
> Kubernetes and are deployed by `./scripts/deploy-k8s.sh` or, in production,
> by the Jenkins pipeline (`ci/jenkins/Jenkinsfile`).

### Option B: Kubernetes (Docker Desktop) — microservices

```bash
# 1. Enable Kubernetes in Docker Desktop first
#    Docker Desktop → Settings → Kubernetes → Enable Kubernetes

# 2. Confirm K8s context
kubectl config use-context docker-desktop

# 3. Start infra (Option A, left running), then deploy microservices one-by-one
./scripts/deploy-k8s.sh --env dev --local
```

Optional: deploy a single microservice at a time (true one-by-one promotion):

```bash
./scripts/deploy-k8s.sh --env dev --local --service config-tenant-service
```

The full CI/CD (Jenkins + SonarQube on K8s) is described in
`ci/helm/values.yaml` and `ci/README.md`. The complete pipeline —
Compile → Unit Test → JaCoCo → SonarQube → Semgrep → Gitleaks →
Dependency Scan → Docker Build → Trivy → Deploy DEV → API Automation → ZAP →
SIT → Regression → UAT → Canary → Sentry/Grafana Monitoring — is defined in
`ci/jenkins/Jenkinsfile`.

## Access Points

Once running, the platform is accessible at:

| Service | URL | Notes |
|---|---|---|
| **API Gateway** | http://localhost:8080 | All requests enter here |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | Auto-generated API docs |
| **Mongo Express** | http://localhost:8081 | admin / Selfcare_M0ng0Expr3ss_Pa55w0rd!2026 |
| **PHPMyAdmin** | http://localhost:8080 | server: mysql, user: selfcare |
| **MongoDB** | localhost:27017 | selfcare / Selfcare_M0ng0_Db_Pa55w0rd!2026 |
| **MySQL** | localhost:3306 | selfcare / Selfcare_My5ql_Db_Pa55w0rd!2026 |
| **Redis** | localhost:6379 | password: Selfcare_R3d1s_Pa55w0rd!2026 |
| **Kafka** | localhost:9092 | |

For Kubernetes, replace `localhost:8080` with `localhost:30080`, etc.

### Per-service access (Kubernetes / NodePort 30080)

| Service | Gateway route | Local port |
|---|---|---|
| API Gateway | http://localhost:30080 | 80 → 8080 |
| Admin portal (selfcare Studio) | http://localhost:30081 | 80 |
| Customer Identity (auth/OTP/JWKS) | http://localhost:30080/api/v1/auth/** | 8081 |
| Admin Identity (SAML/RBAC login) | http://localhost:30080/api/v1/admin/** | 8082 |
| Config Tenant (tenant config) | http://localhost:30080/api/v1/config/** | 8083 |
| Account & Entitlement | http://localhost:30080/api/v1/account/** | 8084 |
| Dashboard BFF | http://localhost:30080/api/v1/dashboard/** | 8085 |
| Product Catalog | http://localhost:30080/api/v1/product/** | 8086 |
| Usage & Balance | http://localhost:30080/api/v1/usage/** | 8087 |
| Billing | http://localhost:30080/api/v1/billing/** | 8088 |
| Payment | http://localhost:30080/api/v1/payment/** | 8089 |
| Notification | http://localhost:30080/api/v1/notification/** | 8090 |
| Content (CMS/FAQ) | http://localhost:30080/api/v1/content/** | 8091 |
| Journey | http://localhost:30080/api/v1/journey/** | 8092 |
| Reporting | http://localhost:30080/api/v1/reporting/** | 8093 |
| AI Gateway | http://localhost:30080/api/v1/ai/** | 8094 |
| Audit | http://localhost:30080/api/v1/audit/** | 8095 |
| Insurance | http://localhost:30080/api/v1/insurance/** | 8096 |
| Approval | http://localhost:30080/api/v1/approval/** | 8097 |
| Support | http://localhost:30080/api/v1/support/** | 8098 |

Direct per-service access (port-forward):

```bash
kubectl port-forward -n selfcare-dev svc/customer-identity-service 8081:8081
kubectl port-forward -n selfcare-dev svc/product-service 8086:8086
```

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

## Production Deployment (AWS EKS / on-prem Kubernetes)

For production deployment via CI/CD:

- `ci/jenkins/Jenkinsfile` — full pipeline (Compile → Unit Test → JaCoCo →
  SonarQube → Semgrep → Gitleaks → Dependency Scan → Docker Build → Trivy →
  Deploy DEV → API Automation → ZAP → SIT → Regression → UAT → Canary →
  Sentry/Grafana Monitoring); deploys microservices ONE BY ONE
- `backend/deploy/helm/values/` — per-environment Helm values (dev/stg/reg/prod)
- `ci/helm/` — Kubernetes chart for Jenkins + SonarQube
- `backend/deploy/gitops/applicationset.yaml` — ArgoCD ApplicationSet

The release pipeline:
1. Builds images in CI (GitHub Actions or Jenkins)
2. Signs with cosign (keyless, GitHub OIDC)
3. Generates SLSA L3 provenance
4. Pushes to registry
5. Deploys each microservice one-by-one via `scripts/deploy-k8s.sh` /
   Jenkins (Helm, `deploy.isolated=true`), promoting dev → stg → reg → prod

## Useful Commands

```bash
# View all running infra services
docker compose ps

# Tail infra logs
docker compose logs -f kafka
docker compose logs -f mongodb

# Restart a single infra service
docker compose restart mysql

# Open a shell in a container
docker exec -it selfcare-infra-mongodb mongosh
docker exec -it selfcare-infra-mysql mysql -uselfcare -pSelfcare_My5ql_Db_Pa55w0rd!2026

# Check tenant config
docker exec -it selfcare-infra-mongodb mongosh \
  mongodb://selfcare:Selfcare_M0ng0_Db_Pa55w0rd!2026@localhost:27017/selfcare_config?authSource=admin \
  --eval "db.tenants.find().pretty()"

# Tear down everything (infra + volumes)
docker compose down -v

# Deploy microservices to K8s (one-by-one)
./scripts/deploy-k8s.sh --env dev --local

# Kubernetes equivalents
kubectl get pods -n selfcare-dev
kubectl logs -n selfcare-dev deployment/api-gateway
kubectl port-forward -n selfcare-dev svc/api-gateway 8080:8080
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

### Microservice pods fail to start / 502 from API gateway
- Check microservice pods: `kubectl get pods -n selfcare-dev`
- Check infra is running (compose): `docker compose ps`
- Check service logs: `kubectl logs -n selfcare-dev deployment/<service>`
- Verify tenant header is set: `X-Tenant-Id: dialog-lk`
- In local (docker-desktop) mode, services reach infra via the compose host —
  ensure the infra cluster services point at the correct producer addresses

### "Cannot connect to MongoDB"
```bash
docker compose logs mongodb
docker exec selfcare-infra-mongodb mongosh --eval "db.adminCommand('ping')"
```

> **Spring Boot 4.1 note:** the MongoDB property prefix is `spring.mongodb.*`,
> not `spring.data.mongodb.*`. Config is injected per-service from the Helm
> chart / Sealed Secret (SPRING_MONGODB_URI / ...). If pods log
> `hosts=[localhost:27017]`, those env keys are missing.

### "API Gateway returns 502"
- Check infra containers: `docker compose ps`
- Check microservice pods/health in K8s: `kubectl get pods -n selfcare-dev`
- Check API Gateway logs: `kubectl logs -n selfcare-dev deployment/api-gateway`
- Verify tenant header is set: `X-Tenant-Id: dialog-lk`

### "Build fails with Maven errors"
- Ensure you're using Java 25 (`java -version`)
- Check `pom.xml` syntax
- Re-run with `--no-cache`

### "Out of memory in Docker Desktop"
- Increase Docker Desktop memory: Settings → Resources → Memory → 8 GB
