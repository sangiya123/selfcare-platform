# Selfcare Platform — Deployment

This directory contains the Helm chart, Docker Compose, and deployment
infrastructure for the platform.

## Contents

| Path | Purpose |
|---|---|
| `helm/` | Helm chart (one chart for all services) |
| `docker-compose.dev.yml` | Local development infrastructure (MySQL, Mongo, Redis, Kafka, Prometheus, Grafana, Jaeger) |
| `prometheus.yml` | Prometheus scrape config for local dev |
| `Jenkinsfile` | CI/CD pipeline (build, test, deploy) |

## Local development

Start infrastructure only (backend services run via Maven):

```bash
cd backend/deploy
docker-compose -f docker-compose.dev.yml up -d
```

| Service | Port | Credentials |
|---|---|---|
| MySQL | 3306 | root / selfcare |
| MongoDB | 27017 | selfcare / selfcare |
| Redis | 6379 | (no password) |
| Kafka | 9092 | (no auth) |
| Prometheus | 9090 | - |
| Grafana | 3000 | admin / selfcare |
| Jaeger | 16686 | - |

## Helm chart

The chart deploys a single service instance. Use it to deploy to Kubernetes.

```bash
# Install with default values
helm install selfcare-customer-identity \
    backend/deploy/helm/ \
    --namespace selfcare-stg \
    --values backend/deploy/helm/values/customer-identity-values.yaml

# Upgrade
helm upgrade selfcare-customer-identity \
    backend/deploy/helm/ \
    --namespace selfcare-stg \
    --values backend/deploy/helm/values/customer-identity-values.yaml

# Uninstall
helm uninstall selfcare-customer-identity --namespace selfcare-stg
```

### Per-service values files

| Service | Values file |
|---|---|
| api-gateway | `values/api-gateway-values.yaml` |
| customer-identity-service | `values/customer-identity-values.yaml` |
| dashboard-bff | `values/dashboard-bff-values.yaml` |
| payment-service | `values/payment-service-values.yaml` |
| Dialog-specific | `values/client-dialog-lk-values.yaml` |
| AIA-specific | `values/client-aia-lk-values.yaml` |

## Environments

| Env | Cluster | Notes |
|---|---|---|
| dev | local docker-compose | single replica, debug logging |
| stg | staging K8s | 2 replicas, staging DBs |
| reg | regression K8s | 1 replica, smoke tests before prod |
| prod | production K8s | 3+ replicas, HPA, PDB |

## Secrets

Production secrets are NOT in git. They are:
- Stored in HashiCorp Vault
- Injected via Vault Agent Sidecar
- Mounted as K8s Secrets in the pod

## Related

- [`../../observability/`](../../observability/) — Grafana dashboards, alerts, runbooks
- [`../../config-schema/`](../../config-schema/) — JSON Schemas for config validation
