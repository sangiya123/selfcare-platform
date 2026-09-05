# ADR-016: Idempotency-Key Header Convention

## Status
Accepted — 2026-09-04

## Context
The API Standards spec mandates that all financial or state-changing
operations use an `Idempotency-Key` header. The same key + same body
must return the existing result; same key + materially different body
must be rejected.

Multiple services need a shared convention:
- Payment service (payment processing)
- Recharge service (topup)
- Activation service (package activation)
- Admin services (publish, role change)

## Decision
We adopt the following convention:

### 1. Header
- `Idempotency-Key: <UUIDv4>` (preferred) or any unique string the client controls
- Required for: POST `/payments`, POST `/recharges`, POST `/activations`, POST `/admin/*/publish`
- Optional but recommended for: POST `/journeys/*/execute`

### 2. Server-side behavior
- On first request: persist `(key, tenantId, requestHash, response, createdAt)` with TTL
- On retry with same key + same hash: return the persisted response (HTTP 200/201)
- On retry with same key + different hash: HTTP 422 with `IDEMPOTENCY_KEY_CONFLICT`
- On retry after TTL: treat as new request

### 3. TTL
- 24 hours default (configurable per service)
- Background job cleans up records past TTL

### 4. Request hash
- SHA-256 of: `tenantId + userId + path + method + body`
- Truncated to 16 bytes (32 hex chars) for storage efficiency

### 5. Storage
- Per-service table in MySQL (e.g. `payment_idempotency`)
- Composite key: `(tenantId, idempotencyKey)` — UNIQUE
- Index: `created_at` for cleanup job

### 6. Cross-service
- Each service maintains its own idempotency table — no shared store
- Same key in payment and recharge are independent

## Implementation
- `IdempotencyService` in `platform-common`
- `IdempotencyKey` annotation on controller methods
- AOP aspect intercepts and enforces the contract
- Test: `IdempotencyKeyTest` (conformance suite)

## Consequences

Positive:
- Network retries are safe for clients
- No duplicate charges on payment retry
- Clear ownership of duplicate prevention

Negative:
- Each service needs an idempotency table
- 24h storage means operational cleanup
- Hashing cost on every write (~1ms)

## Compliance
- API Standards spec § "Idempotency"
- NFR-AVL-004: writes must never claim success without durable outcome
