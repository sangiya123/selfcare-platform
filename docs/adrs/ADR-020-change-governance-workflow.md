# ADR-020: Change Governance Workflow State Machine

## Status
Accepted — 2026-09-04

## Context
The platform must support the full change lifecycle for high-risk
configurations:
Draft → Validate → Preview → Review → Approve → Publish → Monitor → Rollback

This applies to:
- Layout/page config
- Theme updates
- Journey modifications
- Integration credential changes
- Feature flag 100% rollout
- AI tool permission changes
- RAG source additions
- Reports exposing customer data
- Role privilege escalations

The spec requires:
- Git-style diff and immutable version IDs
- Maker-checker (four-eyes) for high-risk changes
- Atomic rollback
- Audit trail of every state transition

## Decision
We implement a **state machine** in the `change-governance-service`
(new microservice, also accessible via `approval-service` for high-risk
admin actions):

### 1. States
```
DRAFT → VALIDATING → VALIDATED
                  ↘ INVALID (→ DRAFT or CANCELLED)
VALIDATED → PREVIEWING → PREVIEWED
PREVIEWED → IN_REVIEW
IN_REVIEW → APPROVED / REJECTED / WITHDRAWN
APPROVED → PUBLISHING → PUBLISHED | PUBLISH_FAILED
PUBLISHED → MONITORING (auto)
MONITORING → STABLE | DEGRADED | FAILED
ANY → ROLLBACK_INITIATED → ROLLED_BACK | ROLLBACK_FAILED
ANY → CANCELLED
```

### 2. Transitions
- Each transition has: actor, timestamp, reason, ticket reference
- Some transitions require a second-actor approval (four-eyes)
- Some transitions require permission (e.g. PUBLISH needs PUBLISHER role)

### 3. Versioning
- Every change is a `ChangeRequest` with `versionId = UUIDv7`
- Publish creates an immutable `ChangeVersion` record
- Previous N versions are retained for rollback (configurable, default 20)
- Version is referenced from `configVersion` in runtime manifest

### 4. Diff
- JSON diff (RFC 6902 JSON Patch) between consecutive versions
- Displayed in admin UI as: added, removed, changed keys
- Critical sections highlighted (auth, payment, security)

### 5. Rollback
- Atomic: either fully applied or fully reverted
- Rollback creates a new `ChangeVersion` (not a delete)
- Triggers config re-compile and re-publish
- Old version becomes the active one
- New version is marked as "ROLLED_BACK" but retained in history

### 6. Approvals
- Stored in `approval_requests` table
- High-risk actions: LAYOUT_PUBLISH, AUTH_POLICY_CHANGE, INTEGRATION_CREDENTIAL_CHANGE,
  PAYMENT_JOURNEY_CHANGE, FEATURE_ENABLE_100, AI_TOOL_PERMISSION_CHANGE,
  RAG_SOURCE_ADDITION, REPORT_CUSTOMER_DATA, ROLE_PRIVILEGE_ESCALATION
- Four-eyes: requester != approver
- Approval has TTL: 72h default (auto-EXPIRED if no decision)
- Approver sees full diff + ticket reference + comments

### 7. Audit
- Every state transition emits an audit event
- Mandatory fields: actor, tenant, environment, role, IP, timestamp,
  before/after version/hash, approval chain, ticket reference, result,
  correlation ID

### 8. Monitoring
- After PUBLISH, system auto-monitors for 5 minutes
- If error rate increases > 2x baseline: auto-ROLLBACK
- Operator notification on auto-rollback
- Configurable per change type

## API
- `POST /api/v1/admin/changes` — create draft
- `GET /api/v1/admin/changes/{id}` — view
- `POST /api/v1/admin/changes/{id}/validate` — run validation
- `POST /api/v1/admin/changes/{id}/preview` — generate preview
- `POST /api/v1/admin/changes/{id}/submit-review` — move to IN_REVIEW
- `POST /api/v1/admin/changes/{id}/approve` — approve (four-eyes)
- `POST /api/v1/admin/changes/{id}/reject` — reject
- `POST /api/v1/admin/changes/{id}/publish` — publish
- `POST /api/v1/admin/changes/{id}/rollback` — rollback
- `GET /api/v1/admin/changes/{id}/diff?from=v1&to=v2` — get diff

## Consequences

Positive:
- Clear ownership of every change
- Atomic rollback reduces blast radius
- Audit trail for compliance
- Four-eyes prevents single-actor compromise

Negative:
- Slower for trivial changes (must go through review)
- Approval timeout can block production
- Monitoring window may be too short/long for some change types

## Compliance
- Admin Scope §16: Change governance
- Spec § "Approval matrix"
- NFR-AUDIT: immutable audit
- Approval matrix in `05_admin_reports/03_Admin_RBAC_Audit_and_Approval_Matrix.md`
