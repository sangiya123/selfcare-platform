# ADR-007: Tenant Isolation Strategy

## Status
Accepted — 2026-09-03

## Context
The platform is multi-tenant (Dialog, Hutch, Airtel, AIA) and must
prevent any cross-tenant data access. Isolation must hold at every
layer: HTTP, service-to-service, database, cache, and event bus.

## Decision
We implement **defense in depth** with five isolation layers:

### 1. HTTP request layer
- Every request carries `X-Tenant-Id` header
- `TenantResolverFilter` (api-gateway) populates `TenantContext` ThreadLocal
- If missing, request returns 400
- If tenant is suspended, request returns 403

### 2. Service-to-service layer
- `TenantContext` is passed in HTTP headers between services
- Outbound `WebClient` calls add `X-Tenant-Id` automatically via interceptor
- In-process ThreadLocal is the canonical scope

### 3. Database layer
- Every query includes `tenantId` as a filter
- MySQL row-level: `tenant_id` is part of every composite key
- MongoDB: `tenantId` is the first segment of all compound indexes
- Database users are service-specific; cross-service reads are blocked at the DB level

### 4. Cache layer
- Every Redis key is prefixed: `omobio:{tenantId}:{key}`
- `TenantAwareRedisTemplate` enforces prefix on all operations

### 5. Event/Kafka layer
- Kafka messages have `tenantId` in the header
- Consumers filter by tenant context
- The platform-common library provides `TenantContext` propagation

## Verification
A conformance test suite (`tests/conformance/tenant/`) verifies that:
- A token issued for tenant A cannot read tenant B's data
- A user without `X-Tenant-Id` is rejected
- A user with a tampered header is rejected

## Consequences

- Tenant context is always available; no need to pass it explicitly
- Performance: tenant filters are indexed
- New services must follow the pattern (no shortcuts)

## Alternatives considered

- **Row-level security in MySQL**: rejected — complex to manage
- **Per-tenant databases**: rejected — too many connections, too much operational overhead
- **Schema per tenant**: rejected — migrations become a nightmare
