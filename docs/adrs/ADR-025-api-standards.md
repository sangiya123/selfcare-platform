# ADR-025: API Standards — Error Envelope, Versioning, and Conventions

## Status
Accepted — 2026-09-04

## Context
The platform exposes REST APIs across:
- Public customer APIs (mobile app)
- Admin APIs (Selfcare Studio)
- Internal service-to-service APIs (between microservices)
- Integration APIs (third-party callbacks)

The API Standards spec mandates:
- Standard error envelope
- Semantic versioning (v1, v2)
- Idempotency keys for stateful operations
- Pagination with cursor-based tokens
- Correlation ID on every request
- Rate limiting headers

## Decision

### 1. Error envelope
Every error response (HTTP 4xx / 5xx) follows this schema:
```json
{
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Customer with id 'xyz' not found",
    "correlationId": "01HV...",
    "retryable": false,
    "details": []
  }
}
```
- `code`: SCREAMING_SNAKE_CASE, machine-readable error code
- `message`: human-readable, safe to display (never contains raw PII)
- `correlationId`: trace ID from OpenTelemetry
- `retryable`: `true` only for 503 / 429; `false` for all client errors
- `details`: optional array of sub-errors (e.g. field validation failures)

### 2. API versioning
- Version in URL path: `/api/v1/...`
- Header `Accept: application/vnd.selfcare.v1+json`
- Breaking changes require a new major version
- Non-breaking additions: add to response, add new optional parameters
- Deprecation: `Deprecation: true` + `Sunset: <date>` headers + `X-Api-Deprecated: true`
- Old versions supported for minimum 6 months after deprecation

### 3. Idempotency
- `Idempotency-Key: <UUIDv4>` header on all POST/PATCH/PUT stateful calls
- Server stores `(key, requestHash, response)` with 24h TTL
- Same key + same hash → return cached response
- Same key + different hash → 422 `IDEMPOTENCY_KEY_CONFLICT`
- See ADR-016 for full convention

### 4. Pagination
- Cursor-based pagination for collections
- Request: `GET /users?cursor=abc&limit=50`
- Response: `{ "data": [...], "nextCursor": "def", "hasMore": true }`
- Default limit: 20; max: 100
- Offsets NOT used (cursor avoids drift on inserts)

### 5. Rate limiting
- Per-tenant rate limits (configurable)
- Headers on every response:
  - `X-RateLimit-Limit: 1000`
  - `X-RateLimit-Remaining: 847`
  - `X-RateLimit-Reset: 1720000000`
- HTTP 429 when exceeded: `{ "error": { "code": "RATE_LIMIT_EXCEEDED", "retryAfter": 30 } }`

### 6. Content negotiation
- Default: `application/json`
- Optional: `Accept: application/vnd.selfcare.v1+json`
- File downloads: `Accept: text/csv`, `application/pdf`

### 7. Correlation ID
- Request: `X-Correlation-Id` header (optional, server generates if absent)
- Propagated via W3C `traceparent` to all downstream calls
- Logged in MDC under `correlationId`
- Required in all error responses

### 8. HTTP methods
- `GET` — read (safe, idempotent)
- `POST` — create (state-changing)
- `PUT` — replace (idempotent)
- `PATCH` — partial update (idempotent if using JSON merge-patch)
- `DELETE` — remove (not idempotent unless using soft-delete)

### 9. Path conventions
- `/api/v1/{resource}` — collection
- `/api/v1/{resource}/{id}` — individual
- `/api/v1/{resource}/{id}/sub-resource` — nested
- No verbs in paths: use HTTP methods

## Implementation
- `GlobalExceptionHandler` in `platform-common` enforces error envelope
- `CorrelationIdFilter` in `platform-common` sets/generates correlation ID
- `RateLimitFilter` in `api-gateway` enforces per-tenant limits
- `IdempotencyService` in `platform-common` (AOP aspect)
- OpenAPI 3.0 spec generated from controller annotations

## Consequences

Positive:
- Consistent developer experience across all APIs
- Predictable error handling for clients
- No confusion about versioning strategy

Negative:
- Strict conventions require discipline across teams
- PATCH semantics can be confusing

## Compliance
- API Standards spec § "Error", "Versioning", "Idempotency"
- NFR-AVL-004: every write is idempotent-keyed
