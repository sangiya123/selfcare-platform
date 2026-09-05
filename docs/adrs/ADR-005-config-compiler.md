# ADR-005: Config Compiler Pipeline

## Status
Accepted — 2026-09-03

## Context
Configuration in MongoDB is the source of truth. At runtime, services
need a fast, immutable, versioned manifest. We need a deterministic
compilation step that:

- Validates against JSON Schema
- Resolves cross-references (componentIds → registry entries, etc.)
- Computes derived data (sorted navigation, flattened layouts, etc.)
- Produces a content-addressed, immutable artifact
- Triggers a single cache invalidation when published

## Decision
We implement a **Config Compiler** that runs as part of the publish
workflow in `config-tenant-service`. It produces an immutable runtime
manifest that downstream services cache.

### Pipeline

```
[ Draft config ]  →  [ validate ]  →  [ resolve refs ]  →  [ derive ]  →  [ sign ]  →  [ publish ]
```

1. **Validate** against JSON Schema (config-schema/schemas/*)
2. **Resolve** cross-references (e.g., componentId → registered component)
3. **Derive** secondary data (sorted, flattened, pre-computed lookups)
4. **Sign** with a content hash (SHA-256) to make the artifact immutable
5. **Publish** to the runtime cache and notify downstream services

### Artifact shape

```json
{
  "version": "1.4.2",
  "tenantId": "dialog-lk",
  "compiledAt": "2026-09-03T12:00:00Z",
  "contentHash": "sha256:abc123...",
  "theme": { ... },
  "experiences": {
    "home": { ... },
    "bills": { ... }
  },
  "navigation": { ... },
  "components": { ... }
}
```

### Caching

- **L1 (in-process)**: Caffeine cache, 100 entries, 5-min TTL
- **L2 (Redis)**: per-tenant manifest, 1-hour TTL
- **L3 (MongoDB)**: durable, queryable by version

The mobile SDK uses ETag-based conditional GET to validate the cache.

## Consequences

- Config changes are atomic (single new version replaces all caches)
- Rollback is just "re-publish previous version"
- The compiler is pure (no side effects), easy to test
- Validation errors are caught before publish (no partial configs in prod)

## Alternatives considered

- **Live queries**: rejected — too slow for runtime
- **CDN distribution**: deferred — not needed at current scale
- **GraphQL config API**: rejected — JSON manifests are simpler and cacheable
