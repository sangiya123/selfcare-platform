# Multi-Tenant Configuration

This document describes how the platform handles multi-tenancy: the rules,
boundaries, and operational practices for running multiple clients on a
single deployment.

## Tenant definition

A **tenant** is a business that uses the OMOBIO platform to deliver a
selfcare product. Each tenant:
- Has a unique `tenantId` (e.g. `dialog-lk`, `aia-lk`)
- Has its own data (strict isolation)
- Has its own branding (theme, logo)
- Has its own configuration (layouts, journeys, notifications)
- Has its own provider integrations (BSS URLs, credentials, etc.)
- Has its own industry (telco, insurance, ...)

## Tenant identification

Every request MUST carry an `X-Tenant-Id` header. The API gateway
enforces this and rejects requests with missing/invalid IDs (HTTP 400).

Internally, the value is propagated via:
- HTTP header on downstream service calls
- `TenantContext` ThreadLocal
- JWT claim `tenantId`
- Kafka message header
- MDC logging field

## Data isolation

### Per-tenant database (recommended for production)
Each tenant has its own MySQL database. This is the strictest isolation
and the recommended setup for regulated industries.

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql.omobio.io/${TENANT_DATABASE:omobio_selfcare_dialog}
```

### Shared database with tenant_id column (cost-optimized)
For cost-sensitive deployments, a shared database is acceptable if every
table has a `tenant_id` column and every query includes the predicate.

The platform's repositories use a `TenantAwareRepository` base class that
auto-injects the `tenant_id` predicate.

### Cache isolation
Every Redis key MUST include `tenantId` in the key:
```
omobio:<service>:<tenantId>:<key>
```

The platform-common `RedisKey` utility enforces this pattern.

## Configuration storage

Per-tenant configuration lives in MongoDB:
- `tenant_configs` (master record)
- `theme_documents` (per tenant)
- `layout_documents` (per tenant, per page)
- `client_integrations` (provider URLs, credentials, etc.)

Updates go through Selfcare Studio (admin UI) and the
config-tenant-service (port 8083).

## Per-tenant customization without code changes

Everything visible to the user is configurable:
- Theme (colors, typography, spacing)
- Layout (page sections, widget placement, props)
- Navigation (tabs, menus, deep links)
- Feature flags
- Notification templates
- Journey definitions
- Provider bindings (URLs, credentials)
- Currency, locale, timezone

If you find yourself wanting to fork the code for a tenant, you're
missing a config option. File a request to add it.

## Industry-specific configuration

Tenants declare their industry in the master record:
```json
{
  "tenantId": "dialog-lk",
  "industry": "telco",
  "providerPack": "telco/dialog"
}
```

The platform uses this to:
- Load the right provider pack (telco/dialog vs insurance/aia)
- Show industry-appropriate widgets in the dashboard
- Validate industry-specific fields in config (e.g. MSISDN for telco)

## Tenant onboarding

1. Create the tenant record in `tenant_configs`
2. Add to `client_integrations` (provider URLs, credentials)
3. Configure theme and at least one layout
4. Run the conformance suite ([CONFORMANCE_SUITE.md](CONFORMANCE_SUITE.md))
5. Publish initial config
6. Test in staging
7. Go live

See [CLIENT_ONBOARDING.md](CLIENT_ONBOARDING.md) for the full guide.

## Tenant offboarding

1. Mark tenant `status = "OFFBOARDED"`
2. Schedule data deletion (regulatory requirement)
3. After retention period, delete:
   - Tenant data from MySQL
   - Tenant config from MongoDB
   - Tenant cache from Redis
   - Tenant integrations from audit log
4. Confirm deletion in audit log

## Monitoring

Per-tenant dashboards in Grafana:
- Request count
- Error rate
- p50/p95/p99 latency
- Widget availability

All metrics are labeled with `tenant_id`.

## Security

- Tokens are tenant-scoped
- Cross-tenant access is rejected (HTTP 403)
- Audit log records all cross-tenant access attempts
- Run `omobio.conformance.tenant-isolation` regularly to verify isolation

## Limits

Each tenant has soft limits (configurable in `tenant_configs`):
- Max users: 1M
- Max sessions: 100k concurrent
- Max storage: 100GB
- Max API calls/min: 60k

Exceeding limits triggers backpressure (HTTP 429).
