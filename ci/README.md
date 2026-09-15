# Selfcare — CI/CD (AEE Flow Matrix)

Config-driven pipeline: every stage is a **named flow** toggled on/off per environment.
Flip booleans in the `FLOWS` map in `Jenkinsfile` to enable/disable any stage for any env.

## Flow Matrix

| Flow             | DEV | STG | REG | PROD | Description                        |
|------------------|-----|-----|-----|------|------------------------------------|
| `compile`        | ON  | ON  | ON  | ON   | `mvn package` (skip tests)         |
| `unit_test`      | OFF | ON  | ON  | ON   | `mvn verify` / JaCoCo (prod)       |
| `admin_qa`       | OFF | ON  | ON  | ON   | Admin portal typecheck + test      |
| `mobile_qa`      | OFF | ON  | ON  | ON   | Mobile app typecheck + test        |
| `sonar`          | OFF | OFF | ON  | ON   | SonarQube scan + quality gate      |
| `security_scan`  | OFF | OFF | OFF | ON   | Semgrep + Gitleaks + Trivy + OWASP |
| `docker_build`   | ON  | ON  | ON  | ON   | Docker build + push to registry    |
| `deploy_dev`     | ON  | ON  | ON  | ON   | Deploy to dev EKS namespace        |
| `deploy_stg`     | OFF | ON  | OFF | OFF  | Deploy to stg via ArgoCD           |
| `deploy_prod`    | OFF | OFF | OFF | ON   | Deploy to prod via ArgoCD          |
| `api_automation` | OFF | ON  | ON  | ON   | Conformance API tests              |
| `zap_dast`       | OFF | OFF | OFF | ON   | OWASP ZAP baseline scan            |
| `sit_regression` | OFF | ON  | ON  | ON   | Smoke + regression tests           |
| `uat_approval`   | OFF | OFF | ON  | ON   | Manual UAT approval gate           |
| `canary`         | OFF | OFF | OFF | ON   | Canary deploy via ArgoCD           |
| `monitoring`     | OFF | OFF | OFF | ON   | Sentry/Grafana/Prometheus verify   |

## How to run

### Option A: One master job (parameterized)

Create a single Jenkins Pipeline job with script path `Jenkinsfile`. Pick
`DEPLOY_ENV` at build time. Flows auto-toggle per the matrix above.

### Option B: Per-env jobs (delegation)

Create four separate Jenkins jobs pointing at:

| Job name             | Script path                  | DEPLOY_ENV |
|----------------------|------------------------------|------------|
| `selfcare-Dev`         | `ci/jenkins/Jenkinsfile.dev` | dev        |
| `selfcare-Staging`     | `ci/jenkins/Jenkinsfile.stg` | stg        |
| `selfcare-Regression`  | `ci/jenkins/Jenkinsfile.reg` | reg        |
| `selfcare-Production`  | `ci/jenkins/Jenkinsfile.prod`| prod       |

Each wrapper calls the master job with the env pinned. `RELEASE_TAG` is
required for `reg` and `prod`.

### Option C: Manual / local

```bash
# Build + deploy to docker-desktop (fast inner loop)
make dev-test

# EKS deploy
make k8s-dev TAG=dev-42 REGISTRY=ghcr.io/sangiya123
make k8s-stg TAG=rc-42 REGISTRY=ghcr.io/sangiya123
make k8s-prod TAG=v1.2.0 REGISTRY=ghcr.io/sangiya123

# ArgoCD GitOps sync
make stg TAG=rc-42
make prod TAG=v1.2.0

# Post-deploy smoke
make smoke ENV=dev
```

## Force overrides

Jenkins parameters `FORCE_QA` and `FORCE_SECURITY` override the flow matrix
for ad-hoc runs (e.g. run security scans on dev without editing the Jenkinsfile).

`SKIP_DEPLOY` disables all deploy + canary flows (build + scan only).

## Setup

1. Copy `ci/.env.ci.example` to `ci/.env.ci`
2. Set credentials: Jenkins admin password, SonarQube token, Docker registry,
   ArgoCD token, OWASP NVD key.
3. Start local CI:

```powershell
.\ci\start-local-ci.ps1
```

4. Create the Jenkins job(s) as described above.

## Deployment runtime

- **EKS** — existing clusters, namespaces per tenant per env
  (`selfcare-dialog-lk-dev`, `selfcare-dialog-lk-prod`, etc.)
- **ArgoCD** — ApplicationSet matrix generator fans out GitOps to per-operator
  tenant namespaces. Dev/stg auto-sync, reg/prod approval-gated.
- **Registry** — `ghcr.io/sangiya123/<service>:<tag>`. Dev tags mutable (`dev-*`),
  prod tags immutable (`v1.2.0`).
