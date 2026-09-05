# OMOBIO Selfcare Platform — GitOps with ArgoCD

## Overview

This directory contains GitOps manifests that manage the OMOBIO Selfcare Platform lifecycle
using ArgoCD ApplicationSets. The platform is multi-tenant, multi-environment, and
multi-industry (TELCO, INSURANCE, BANKING, TRAVEL).

## Architecture

```
GitOps Flow
-----------

  Git Repository (selfcare-platform)
    │
    ├── backend/deploy/gitops/          <-- THIS DIRECTORY
    │     ├── applicationset.yaml      <-- ArgoCD ApplicationSet
    │     ├── argocd-project.yaml      <-- AppProject definition
    │     ├── tenants/                  <-- Tenant configurations
    │     ├── environments/            <-- Environment parameters
    │     └── kustomize/               <-- Kustomize base + overlays
    │
    └── backend/deploy/helm/           <-- Helm chart
          ├── values.yaml              <-- Base values
          └── values/client-*-values.yaml  <-- Tenant overrides
```

## How It Works

### 1. ApplicationSet Generation

The `applicationset.yaml` uses ArgoCD's **Matrix Generator** to fan out per-tenant
per-environment ArgoCD Applications:

```
Matrix Generator
    ├── Generator: Git (tenants/*.yaml)    --> [tenant1, tenant2, ...]
    └── Generator: Git (environments/*.yaml) --> [dev, stg, reg, prod]
                    |
                    +--> Cross-product: tenant × environment
                         e.g., dialog-lk + prod = omobio-dialog-lk-prod
```

### 2. Application Resources

Each generated Application:
- **Source**: Points to `backend/deploy/helm/` with tenant-specific `values/client-{tenant}-values.yaml`
- **Destination**: Deploys to namespace `omobio-{tenant}-{env}`
- **Sync Policy**: Automated with prune/selfHeal; `CreateNamespace=true`

### 3. Promotion Model

Following the **build once, promote immutable** principle:

```
Image Tag Strategy
------------------

  dev:    Mutable (latest) — any commit auto-deploys
  stg:    Mutable (latest) — latest promoted to staging
  reg:    Mutable (pinned) — regression team pins specific version
  prod:   Immutable (pinned) — requires explicit approval + sync window

Promotion Path:
  dev --> stg --> reg --> prod
                |
                +--> [Approval gates per environment]

Artifact Immutable:
  Once an image tag is promoted to prod, it MUST NOT change.
  To fix: promote a NEW artifact (new tag), do NOT re-tag.
```

### 4. Tenant Isolation

Each tenant gets its own Kubernetes namespace:
- `omobio-dialog-lk-dev`
- `omobio-dialog-lk-stg`
- `omobio-dialog-lk-prod`
- etc.

RBAC restricts cross-namespace access. ArgoCD AppProject (`argocd-project.yaml`)
defines:
- Source repos
- Destination clusters/namespaces
- Role-based permissions per namespace owner

## File Structure

```
backend/deploy/gitops/
|-- README.md                        # This file
|-- applicationset.yaml              # ArgoCD ApplicationSet (matrix generator)
|-- argocd-project.yaml              # ArgoCD AppProject with multi-tenant RBAC
|-- tenants/                         # Tenant-specific configuration
|   |-- dialog-lk.yaml               # Dialog (Sri Lanka) — TELCO OPERATOR
|   |-- hutch-lk.yaml                # Hutch (Sri Lanka) — TELCO OPERATOR
|   |-- airtel-lk.yaml               # Airtel (Sri Lanka) — TELCO OPERATOR
|   |-- aia-lk.yaml                  # AIA (Sri Lanka) — INSURANCE INSURER
|   |-- aia-sg.yaml                  # AIA (Singapore) — INSURANCE INSURER
|   |-- aia-th.yaml                  # AIA (Thailand) — INSURANCE INSURER
|   |-- aia-my.yaml                  # AIA (Malaysia) — INSURANCE INSURER
|   |-- aia-hk.yaml                  # AIA (Hong Kong) — INSURANCE INSURER
|   `-- aia-in.yaml                  # AIA (India) — INSURANCE INSURER
|-- environments/                    # Environment-specific parameters
|   |-- dev.yaml                     # Development — mutable, no approvals
|   |-- stg.yaml                     # Staging — mutable, basic approvals
|   |-- reg.yaml                     # Regression — mutable, regression team
|   `-- prod.yaml                    # Production — immutable, strict sync windows
`-- kustomize/                       # Kustomize overlays (alternative to Helm)
    |-- base/
    |   `-- kustomization.yaml       # Base resources
    `-- overlays/
        `-- dialog-lk-prod/          # Per-tenant per-env overlay example
            `-- kustomization.yaml
```

## Tenants

| Tenant ID   | Operator | Industry   | Type      | Region |
|-------------|----------|------------|-----------|--------|
| dialog-lk   | Dialog   | TELCO      | OPERATOR  | LK     |
| hutch-lk    | Hutch    | TELCO      | OPERATOR  | LK     |
| airtel-lk   | Airtel   | TELCO      | OPERATOR  | LK     |
| aia-lk      | AIA      | INSURANCE  | INSURER   | LK     |
| aia-sg      | AIA      | INSURANCE  | INSURER   | SG     |
| aia-th      | AIA      | INSURANCE  | INSURER   | TH     |
| aia-my      | AIA      | INSURANCE  | INSURER   | MY     |
| aia-hk      | AIA      | INSURANCE  | INSURER   | HK     |
| aia-in      | AIA      | INSURANCE  | INSURER   | IN     |

## Environments

| Environment | Sync Window     | Image Tag | Required Approvals | Auto-Sync |
|-------------|-----------------|-----------|-------------------|-----------|
| dev         | Always open     | mutable   | None              | Yes       |
| stg         | Business hours  | mutable   | 1 (Developer)     | Yes       |
| reg         | Business hours  | mutable   | 2 (Dev + QA)      | Yes       |
| prod        | Maintenance win | immutable | 3 (Dev + QA + Ops)| No        |

## Sync Windows

Production deployments are restricted to approved maintenance windows:
- **Primary**: Sunday 02:00-04:00 UTC
- **Secondary**: Wednesday 02:00-04:00 UTC
- **Emergency**: Requires incident ticket + 2 approvals

## ArgoCD AppProject RBAC

The `argocd-project.yaml` defines:

```yaml
roles:
  # Tenant namespace owner — can sync their own namespace
  - name: tenant-{tenant-id}-owner
    policies:
      - p, proj:omobio-platform:tenant-{tenant-id}-owner,applications,*,omobio-{tenant-id}-*,allow

  # Environment operator — can sync any tenant in their environment
  - name: env-{env}-operator
    policies:
      - p, proj:omobio-platform:env-{env}-operator,applications,sync,omobio-*-{env},allow

  # Platform admin — full access
  - name: platform-admin
    policies:
      - p, proj:omobio-platform:platform-admin,*,*,*,allow
```

## Promoting a Version

### Manual Promotion (CLI)

```bash
# 1. Tag the release in Git
git tag -a v1.2.3 -m "Release 1.2.3 for dialog-lk prod"
git push origin v1.2.3

# 2. ArgoCD automatically detects the tag and creates a rollout plan
argocd app set omobio-dialog-lk-prod --revision v1.2.3
argocd app sync omobio-dialog-lk-prod

# 3. Wait for sync + health check
argocd app wait omobio-dialog-lk-prod --timeout 600
```

### Automated Promotion (CI/CD)

CI pipeline (Jenkins/GitHub Actions) handles promotion after passing gates:
1. Build Docker image → tag with commit SHA
2. Deploy to dev automatically
3. Run integration tests
4. On success, promote image tag to staging
5. Run regression suite
6. On success, create release tag
7. Notify release managers for prod approval

## Rollback

```bash
# Rollback to previous sync
argocd app rollback omobio-dialog-lk-prod

# Or sync to a specific revision
argocd app sync omobio-dialog-lk-prod --revision v1.2.2
```

## Adding a New Tenant

1. Create `tenants/{new-tenant-id}.yaml` with tenant configuration
2. Create `backend/deploy/helm/values/client-{new-tenant-id}-values.yaml`
3. Commit and push — ArgoCD ApplicationSet auto-generates Applications
4. New Applications appear in ArgoCD UI for manual sync or auto-sync

## Adding a New Environment

1. Create `environments/{new-env}.yaml` with environment parameters
2. Commit and push — ArgoCD ApplicationSet auto-generates Applications
3. Update sync windows in `argocd-project.yaml` if needed

## Kustomize Alternative

For teams preferring Kustomize over Helm, use `kustomize/` overlays:

```bash
# Build and apply overlay
kustomize build kustomize/overlays/dialog-lk-prod | kubectl apply -f -

# Or deploy via ArgoCD with Kustomize generator
# (requires Kustomize Application generator instead of Helm)
```

## Secret Management

Secrets are referenced via external secret managers:
- Kubernetes External Secrets Operator
- Vault Agent
- AWS Secrets Manager / Azure Key Vault

Example in tenant values:
```yaml
env:
  DIALOG_BSS_API_KEY_REF: "secret-ref:dialog-bss-api-key"
  # --> resolved by External Secrets Operator at runtime
```

## Monitoring

- ArgoCD Dashboard: Track sync status across all tenants/environments
- Prometheus Metrics: `argocd_app_sync_total`, `argocd_app_sync_status`
- Alerts: Failed syncs, drift detection, sync window violations

## References

- [ArgoCD ApplicationSet Documentation](https://argo-cd.readthedocs.io/en/stable/user-guide/application-set/)
- [ArgoCD AppProject Documentation](https://argo-cd.readthedocs.io/en/stable/user-guide/projects/)
- [ArgoCD Sync Windows](https://argo-cd.readthedocs.io/en/stable/user-guide/sync-windows/)
- [Kustomize Documentation](https://kubectl.docs.kubernetes.io/guides/introduction/kustomize/)
