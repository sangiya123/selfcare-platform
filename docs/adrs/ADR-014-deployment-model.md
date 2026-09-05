# ADR-014: Tenant Deployment Model — Namespace per Tenant

## Status
Accepted — 2026-09-04

## Context
The platform must support per-tenant (operator/client) isolation at the
deployment level. The spec is open between (a) dedicated EKS cluster per
operator and (b) dedicated Kubernetes namespace per operator.

Each operator may have:
- Independent regulatory/contractual boundary
- Independent release cadence
- Independent compliance audit

The platform must support both deployment topologies and not bake the
choice into application code.

## Decision
We implement a **namespace-per-tenant** model as the baseline, with
**cluster-per-tenant** as the upgrade path for high-risk operators
(insurance underwriters, financial-services clients). The application
layer is unaware of which model is in use — isolation is enforced by:

1. **Kubernetes namespace per tenant** (e.g. `omobio-dialog-lk-prod`)
2. **Per-tenant secrets** referenced as Kubernetes `Secret` or external secret manager
3. **Per-tenant ConfigMap** for non-secret config (read by config-tenant-service at startup)
4. **Per-tenant Redis namespace** (logical DB index + key prefix)
5. **Per-tenant MySQL/Mongo database** (one DB per operator)
6. **NetworkPolicy** in default Helm chart restricts ingress to gateway only

For high-risk operators (financial, healthcare), operators deploy to a
dedicated EKS cluster. CI/CD supports this via the `clusterUrl` field
in the GitOps tenant config (`backend/deploy/gitops/tenants/{tenant}.yaml`).

## Consequences

Positive:
- One platform build serves all deployments — no per-tenant code forks (consistent with ADR-001)
- Easy migration: tenant can move from shared to dedicated cluster without code change
- Cost-effective: most operators stay in shared EKS, only high-risk pay for isolation
- Blast radius: a noisy neighbour or cluster failure is contained to one tenant

Negative:
- Two deployment topologies to test
- Tenant data migration is non-trivial if isolation requirements change
- Per-tenant cluster operators must follow OMOBIO EKS baseline

## Compliance
- Per-tenant data plane isolation (NFR-PRIVACY)
- Cross-tenant data access forbidden (validated by `TenantIsolationTest`)
- Audit log includes `tenant` and `environment` on every record (NFR-AUDIT)
