# ADR-027: SDLC and Release Strategy

## Status
Accepted — 2026-09-04

## Context
The platform has:
- 20+ backend microservices
- 2 frontend apps (mobile, admin)
- Multiple operators with independent release cadences
- Industry packs with per-client variants
- Strict change governance for high-risk configs

The spec requires:
- Zero-downtime deployments
- GitOps configuration management
- Environment promotion (dev → stg → reg → prod)
- Per-tenant config version tracking
- No config changes through manual prod edits

## Decision

### 1. Branching model
- `main` — always deployable to stg
- `release/X.Y.Z` — release branch, only bug fixes merged
- Feature flags used to hide incomplete work (not feature branches)
- No long-lived operator-specific branches

### 2. CI/CD pipeline
```
PR → lint/test/scan → merge to main → build Docker images → deploy to stg
                                                            ↓
                                          smoke tests → integration tests
                                                            ↓
                                          manual approval → deploy to reg
                                                            ↓
                                          performance tests
                                                            ↓
                                          manual approval → deploy to prod
```

### 3. GitOps (ArgoCD)
- All Kubernetes manifests in `backend/deploy/helm/`
- Tenant configs in `backend/deploy/gitops/tenants/{tenant}.yaml`
- ArgoCD `Application` resources sync from git
- No manual `kubectl apply` in prod
- `ImageUpdateAutomation` updates image tags automatically

### 4. Environment promotion
- **dev** — local or ephemeral; built on every commit
- **stg** — mirrors prod topology; nightly builds + on-demand
- **reg** — pre-prod; manual gate; regression suite
- **prod** — blue/green or canary; feature flags for gradual rollout

### 5. Config management
- Config is NOT in the code or Docker images
- Config stored in MongoDB (source of truth) per ADR-004
- Admin portal is the only way to change production config
- Change governance workflow (ADR-020) enforces review for high-risk changes
- Config versions are immutable (releases are snapshots)

### 6. Feature flags over branches
- Use feature flags to ship incomplete features
- Flag `false` in prod, `true` in dev/stg for testing
- Kill switch available in <5s via Redis override

### 7. Release versioning
- Backend services: semantic version (`1.2.3`)
- Frontend apps: semantic version + build number
- Config versions: timestamp-based (`v20240904-143022`)
- Image tags: `1.2.3`, `1.2.3-sha.abc1234`, `latest`

### 8. Rollback
- Code: `helm rollback` (previous image tag)
- Config: `change-governance-service.rollback()` (previous version)
- Database: Flyway migration is NOT rolled back (forward-only)
- Data migration rollback: handled by migration scripts

### 9. Database migrations
- Flyway migrations (forward-only, versioned)
- Every migration is idempotent (can be re-run safely)
- Migrations run on app startup
- No manual schema edits in prod

### 10. Secrets management
- No secrets in git
- Kubernetes `Secret` + external secret manager (Vault / AWS Secrets Manager)
- `SealedSecrets` or `ExternalSecrets` for git-synced secrets
- Rotation: automated for certs, manual notification for API keys

## Implementation
- `backend/Jenkinsfile` — pipeline stages for all services
- `backend/deploy/helm/` — Helm charts per service
- `backend/deploy/gitops/` — ArgoCD applications + tenant configs
- `ci/github-actions/` — GitHub Actions workflows

## Consequences

Positive:
- Zero-downtime deployments
- Complete audit trail for config changes
- Independent operator release cadences

Negative:
- GitOps complexity
- Change governance can slow urgent fixes (kill switch is the exception)
- Migration management overhead
