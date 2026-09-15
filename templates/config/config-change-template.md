# Configuration Change — <tenant>/<env>/<change-name>

| Field | Value |
|---|---|
| **Change ID** | CFG-NNNN |
| **Tenant** | tenant-id (e.g. `dialog-lk`) |
| **Environment** | dev / stg / reg / prod |
| **Change type** | theme / layout / journey / feature-flag / navigation / integration / report |
| **Config version (target)** | semver (e.g. `2.4.1`) |
| **Requester** | name + email |
| **Date** | YYYY-MM-DD |
| **Approval workflow** | draft → validate → preview → approve → publish → rollback |

## Requested Change

Plain-English description of what is being changed and *why*. Link to
the source artifact (Studio draft, Git branch, PR) and any design
specs.

## Affected Surfaces

- **Tenant / industry pack:** …
- **LOB / segment / app versions:** …
- **Components / actions / schemas touched:** …
- **Data sources affected:** …
- **Provider interfaces affected:** …
- **API routes affected:** …

## Impact Analysis

### Security & Compliance

- **PII / consent impact:** …
- **Authorization actor / action / target policy changes:** …
- **Compliance frameworks (GDPR, PCI-DSS, etc.):** …

### Accessibility

- **WCAG criteria affected:** …
- **Accessibility review result:** …

### Compatibility

- **Schema version (must be `2.0` for the current compiler):** …
- **Backward compatibility with previous mobile app version (min):** …
- **Forward compatibility with next mobile app version:** …

## Validation

- **Schema validation result:** ✅ / ❌
- **Reference resolution result:** ✅ / ❌
- **Compatibility matrix result:** ✅ / ❌
- **Forbidden-action policy check:** ✅ / ❌
- **Visual regression (golden screenshot) result:** ✅ / ❌
- **Journey simulation result:** ✅ / ❌
- **Performance regression result:** ✅ / ❌

## Preview Evidence

- **Preview URL:** `https://preview-<env>.selfcare.io/<tenant>/<layout-draft>`
- **Screenshots:** attach
- **Stakeholder sign-off:** name + date for each

## Rollout Plan

- **Canary cohort:** e.g. 5% of users, internal users only, single LOB
- **Step durations:** 5min → 30min → 2h
- **Rollback trigger conditions:** SLO breach, error-rate spike, …
- **Rollback procedure:** revert to previous version in Mongo
- **Rollback tested in:** dev / stg / reg

## Approvals

| Role | Name | Date | Signature |
|---|---|---|---|
| Product owner | | | |
| QA lead | | | |
| Tech lead | | | |
| Security reviewer (if high-risk) | | | |
| Tenant sponsor (if cross-tenant) | | | |

## Rollback Version

| Version | Reason | Date deployed | Date rolled back |
|---|---|---|---|
| v2.4.0 | prior | YYYY-MM-DD | — |
| v2.4.1 (this change) | … | YYYY-MM-DD | (if applicable) |
