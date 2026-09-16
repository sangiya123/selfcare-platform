# =============================================================================
# Selfcare Platform — deploy/kubernetes layout
#
# Deployment topology (v3):
#   - STATEFUL INFRA  -> docker-compose (host): MongoDB, Mongo-Express, Redis,
#     MySQL, PHPMyAdmin, Zookeeper, Kafka. NOT deployed to Kubernetes.
#   - STATELESS APPS  -> Kubernetes: 19 backend microservices + selfcare-admin
#     portal, deployed ONE BY ONE via the umbrella Helm chart
#     (backend/deploy/helm) using deploy.isolated=true. Jenkins drives the
#     per-service promote loop (see ci/jenkins/Jenkinsfile deployAllTo()).
#   - CI/OBSERVABILITY -> Kubernetes (ci/helm): Jenkins, SonarQube,
#     Prometheus, Grafana (see ci/helm).
#
# Files:
#   secrets.yaml             selfcare-infra-creds (referenced by Helm envFrom)
#                            + selfcare-registry-secret placeholder.
#   configmap.yaml           Shared behavior config — RENDERED BY the Helm chart
#                            (selfcare-config-behavior). Only add file-based
#                            config here if you must inject k8s-only values.
#   tenant-seeding-job.yaml  Optional one-shot Job that seeds tenant_configs
#                            into Mongo. Dev seeding is normally handled by
#                            docker-compose volume mounts
#                            (deploy/local/mongo-init/01-tenant-dialog.js) on
#                            the host-facing Mongo.
#   infra/                   (reserved) Intentional ABSENCE: stateful infra is
#                            docker-compose-managed, not in-cluster.
#
# Usage (local docker-desktop):
#   docker compose up -d          # infra only
#   ./scripts/deploy-k8s.sh --env dev --local
#
# Usage (EKS via Jenkins):
#   ./scripts/deploy-k8s.sh --env dev --tag dev-42 --registry ghcr.io/sangiya123
# =============================================================================