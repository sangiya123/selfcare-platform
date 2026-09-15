# ADR-026: Multi-Tenancy Model — Tenant Resolution and Context

## Status
Accepted — 2026-09-04

## Context
The platform serves multiple tenants (operators/clients) from the same
codebase. Every request must be tagged with a tenant. The platform must:
- Resolve tenant from request context
- Enforce tenant isolation at every layer
- Support per-tenant configuration overrides
- Never leak cross-tenant data

## Decision

### 1. Tenant resolution order
Each incoming request resolves the tenant in this priority:
1. **`X-Tenant-Id` header** (highest priority, explicit routing)
2. **JWT `tenant_id` claim** (after authentication)
3. **Subdomain** (`dialog-lk.selfcare.io` → `dialog-lk`)
4. **Path prefix** (`/api/dialog-lk/...` → `dialog-lk`, legacy only)
5. **Default tenant** from config (dev environment only)

### 2. Tenant context propagation
- `TenantContext` (ThreadLocal or Reactor Context wrapper) holds the resolved tenant
- Set once in `TenantResolverFilter` at gateway entry
- Propagated through all downstream service calls via `X-Tenant-Id` header
- Never stored in database — always passed per request

### 3. Row-level tenant isolation
Every MySQL table that holds tenant-scoped data:
```sql
CREATE TABLE accounts (
  id          BIGINT PRIMARY KEY,
  tenant_id   VARCHAR(64) NOT NULL,
  user_id     BIGINT NOT NULL,
  ...
  INDEX idx_tenant_user (tenant_id, user_id),
  CONSTRAINT fk_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);
```
- All queries MUST include `WHERE tenant_id = ?`
- Enforced by: application layer (primary defense), DB triggers (secondary)
- `TenantIsolationTest` (conformance suite) validates no cross-tenant leakage

### 4. MongoDB collection design
Each tenant gets isolated data in MongoDB via:
- `tenantId` field on every document (mandatory, indexed)
- Compound index `{ tenantId: 1, _id: 1 }`
- Collection-level access via `MongoTemplate` with mandatory filter

### 5. Redis isolation
- Key prefix: `selfcare:{tenantId}:{key}`
- Logical DB index per tenant (configurable)
- `RedisTenantFilter` validates prefix on all keys

### 6. Kafka topic design
- Either: one topic per tenant (`dialog-lk.events`, `hutch-lk.events`)
- Or: single shared topic with `tenantId` in message envelope
- Consumer groups are tenant-scoped

### 7. Per-tenant configuration
- Stored in MongoDB `tenant_configs` collection
- Loaded by `config-tenant-service` at startup
- Overrides: global defaults → operator-level → tenant-level
- Runtime overrides via Redis (kill switches, feature flags)

### 8. Tenant metadata
```json
{
  "tenantId": "dialog-lk",
  "name": "Dialog Axiata",
  "industry": "TELCO",
  "tenantType": "OPERATOR",
  "region": "lk",
  "status": "ACTIVE",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

## Implementation
- `TenantResolverFilter` in `platform-common` — sets `TenantContext`
- `TenantContext` utility — `get()`, `getRequired()`, `set()`
- `TenantAware` annotation — marks services/classes as tenant-aware
- `TenantIsolationAspect` — AOP enforcement on repository methods (dev only)
- All repository methods require `tenantId` parameter (compile-time enforcement via custom annotation processor)

## Consequences

Positive:
- Hard isolation between tenants
- Predictable resolution order
- No accidental cross-tenant data access

Negative:
- Every query needs `tenantId` — boilerplate
- Tenant resolution can be a failure point if headers are missing
- MongoDB documents require discipline (no collection-wide reads)

## Compliance
- NFR-PRIVACY: operator data planes isolated
- Tenant isolation test suite validates no cross-tenant access
