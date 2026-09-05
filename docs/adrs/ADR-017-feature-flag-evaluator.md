# ADR-017: Feature Flag Evaluator Strategy

## Status
Accepted — 2026-09-04

## Context
The platform supports a wide variety of feature flags (admin-defined
and operator-scoped). The spec requires:

- Percentage / canary rollout
- Tenant/LOB/segment conditions
- Prerequisites (one flag requires another)
- Kill switch
- App version compatibility (iOS/Android min-max)
- Expiry / owner

Flags must be evaluated fast (sub-millisecond) in the request path
because they're checked on every API call.

## Decision
We adopt a **two-tier evaluation** model:

### Tier 1: In-process compiled ruleset
- At config-tenant-service startup, the platform compiles all active
  feature flags for the current tenant into a `CompiledFlagRuleset`
  (Protobuf-serializable)
- This is held in a `LoadingCache<String, CompiledFlagRuleset>` keyed by tenantId
- TTL: 5 minutes (configurable)
- On flag mutation (admin creates/updates), the cache is invalidated
  via Redis pub/sub (`omobio:flag:invalidate`)

### Tier 2: Real-time override
- For emergency kill switches, a Redis lookup is done in parallel
- Redis key: `omobio:flag:kill:{tenantId}:{flagKey}` → "KILLED"
- If present, evaluation short-circuits to false

### Evaluation order
1. Tenant ID matches (or `scope = all`)
2. Prerequisites all evaluate to true
3. App version (from `X-App-Version` header) within min-max
4. Expiry check (returns false if expired)
5. Targeting rules (AND logic)
6. Percentage rollout (hash of `userId % 100` < rolloutPercent)

### Caching
- Per-tenant compiled ruleset in process memory
- Per-user evaluation result cached in Redis with 30s TTL (key: `flag:{tenantId}:{userId}:{flagKey}`)
- `userId` = primary connection id or admin user id

### Observability
- Every evaluation emits: `feature_flag.exposure` event with flag key, tenant, userId (hashed), variant, result
- Aggregated for analytics: `feature_flag_exposure_total{flag=X,tenant=Y,variant=Z}`

## Vendor-neutral API
- `FeatureFlagClient` interface — admin-side and app-side use the same interface
- Implementations: `InProcessFeatureFlagClient` (default), `LaunchDarklyClient` (future), `OpenFeatureClient` (future)
- All implementations use the same evaluation semantics

## Consequences

Positive:
- Fast evaluation: <1ms in-process
- Vendor-neutral: can swap providers
- Kill switch takes effect within 5s
- Statistics for A/B analysis

Negative:
- Two-tier complexity
- Stale ruleset up to 5min after admin change
- Per-tenant cache memory usage

## Compliance
- OpenFeature concepts (vendor-neutral API)
- NFR-SPEED: config/routing served from process memory
- Spec §6: Feature manager requirements
