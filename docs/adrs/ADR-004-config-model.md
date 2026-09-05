# ADR-004: MongoDB as Config Source of Truth

**Status**: ACCEPTED
**Date**: 2026-09-03
**Deciders**: OMOBIO Architecture Council

## Context

The platform must support per-tenant, per-experience configuration:
- Themes (colors, typography, spacing)
- Layouts (page sections, widget placement, props)
- Navigation (tabs, menus, deep links)
- Feature flags
- Provider bindings
- Journey definitions

This config changes frequently (multiple times per day for active tenants).
It must be:
- Versioned
- Auditable
- Cacheable
- Distributable to mobile/web apps
- Editable via Selfcare Studio

## Decision

**MongoDB as the source of truth.** Config is **compiled at publish time** to
immutable runtime manifests. Mobile/web apps cache manifests locally and
fetch only on version change.

## Why MongoDB

1. **Schema flexibility** — themes, layouts, journeys have varying structures
   per industry (telco vs insurance)
2. **Rich querying** — admin UI needs to filter by tenant, status, version
3. **Embedded documents** — layout sections are naturally nested
4. **TTL indexes** — old draft versions auto-expire
5. **Atomic updates** — optimistic locking via `@Version`

## Why not other stores

- **MySQL** — would require EAV or JSON columns; loses queryability
- **etcd/Consul** — not designed for large config documents
- **Git-as-config** — too slow, no UI

## Compile-at-publish pattern

```java
// LayoutDocument (Mongo)
{
  tenantId: "dialog-lk",
  experience: "home",
  profileKey: "mobile_prepaid",
  configVersion: 184,
  status: "PUBLISHED",
  sections: [...],
  themeRef: "dialog-default@17",
  navigationRef: "dialog-main@9"
}

// CompiledManifest (in-memory + Redis cache)
{
  schemaVersion: "2.0",
  configVersion: 184,
  tenant: "dialog-lk",
  experience: "home",
  sections: [...compiled sections...],
  theme: { ...resolved tokens... },
  navigation: { ...resolved nav... },
  compiledAt: "2026-09-03T15:42:00Z",
  compilerVersion: "1.0.0"
}
```

## Pipeline

1. **Author** — Admin edits layout in Selfcare Studio (creates DRAFT)
2. **Validate** — JSON Schema validation, action type whitelist, component registration check
3. **Preview** — Admin sees rendered preview (DRAFT, not served to apps)
4. **Approve** — Move to REVIEW/APPROVED state
5. **Publish** — ConfigCompiler runs, status → PUBLISHED, configVersion++
6. **Distribute** — Mobile app polls /api/v1/config/manifest, gets new version
7. **Cache** — Redis caches manifest for 30 min; apps cache for the session

## Versioning

- `schemaVersion` (e.g. "2.0") — config format, breaking changes
- `configVersion` (int) — auto-incremented on each publish
- Apps send `If-None-Match: "<tenantId>:<configVersion>"` for 304 Not Modified

## Collections

| Collection | Purpose | Cardinality |
|---|---|---|
| `tenant_configs` | Tenant master record | 1 per tenant |
| `theme_documents` | Design tokens per tenant | 1-10 per tenant |
| `layout_documents` | Page layouts | 10-100 per tenant |
| `navigation_documents` | Tab/menu structure | 1-5 per tenant |
| `journey_definitions` | Multi-step flows | 1-20 per tenant |
| `feature_flags` | Per-tenant toggles | 10-100 per tenant |
| `notification_templates` | i18n templates | 50-500 per tenant |
| `client_integrations` | Per-tenant provider configs | 5-50 per tenant |

## Consequences

### Positive
- Fast reads (Redis cache + immutable manifests)
- Mobile app offline-first (cached manifest survives network outage)
- Atomic publish (no partial updates)
- Auditable via configVersion
- Industry-specific schemas without code changes

### Negative
- Two sources (Mongo + Redis) — must keep in sync (handled by service layer)
- Schema evolution requires migration tooling
- Compiled manifest is large (mitigated by gzip + etag)

## Rollback

Every publish increments configVersion. To roll back, republish a previous
version (the service keeps the last 10 versions of each document).

For emergency rollback, ops can mark `config.published` events as rolled back
and the ConfigCompiler re-runs against the previous version.
