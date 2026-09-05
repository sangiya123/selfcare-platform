# ADR-019: Provider Orchestration — Timeout, Retry, Circuit Breaker

## Status
Accepted — 2026-09-04

## Context
The platform orchestrates dozens of provider integrations per request
(typical dashboard fan-out: 25 providers). Each provider is an
external dependency with potential for:
- Network timeout
- Rate limiting (429)
- Server errors (5xx)
- Malformed responses
- Slow responses

The spec requires:
- Timeout, retry, circuit breaker, bulkhead, fallback
- Conservative retries (never on non-idempotent writes)
- Circuit breaker per provider
- Every call records: provider id, source, correlation id, timeout budget, retry count, circuit state, response category, latency

## Decision
We adopt a **uniform orchestration contract** implemented as a
`ProviderExecutor<T>` in `platform-common`:

### 1. Configuration
Each provider has a `ProviderConfig` (loaded from config-tenant-service):
- `timeoutMs`: default 3000, max 10000
- `retryCount`: default 0 (writes), 1-2 (idempotent reads)
- `retryBackoffMs`: exponential, 200 → 400 → 800
- `circuitBreaker`: enabled, failureRateThreshold 50%, slidingWindow 100
- `bulkhead`: max concurrent calls per provider (default 50)
- `fallbackStrategy`: STALE_CACHE, DEFAULT_VALUE, FAIL_FAST

### 2. Retry policy
- Idempotent reads: retry on 5xx, 429, timeout
- Writes: NEVER auto-retry (require client-side idempotency key)
- 4xx (except 429): no retry, fast-fail
- After max retries: invoke fallback strategy

### 3. Circuit breaker
- Resilience4j-based
- States: CLOSED → OPEN (after threshold) → HALF_OPEN (after wait duration)
- Per-provider circuit (not shared across providers)
- HALF_OPEN: allow 10 calls to test recovery
- Auto-recovery when success rate > 50%

### 4. Bulkhead
- Per-provider semaphore (max concurrent in-flight calls)
- Excess calls return `BulkheadFullException` → fallback strategy
- Prevents one slow provider from blocking all threads

### 5. Fallback hierarchy
1. Try cached response (Redis, max 5min old) — mark as STALE
2. If no cache: use configured DEFAULT_VALUE (e.g. empty array, -1)
3. If no default: FAIL_FAST — return error to caller

### 6. Telemetry
Every call emits: `provider_call_total{provider=X,status=Y,outcome=Z}` metric
+ trace span with attributes (providerId, source, retryCount, circuitState, latencyMs)
+ log entry at INFO/WARN/ERROR based on outcome

### 7. Standard exceptions
- `ProviderTimeoutException` — past timeoutMs
- `ProviderRateLimitException` — 429 received
- `ProviderErrorException` — 5xx received
- `ProviderMalformedException` — body parse failure
- `ProviderCircuitOpenException` — circuit breaker is OPEN
- `ProviderBulkheadFullException` — too many concurrent calls

## Implementation
- `ProviderExecutor` (in `platform-common`) wraps the call with all policies
- Provider beans call through `ProviderExecutor` instead of direct HTTP
- All providers instrumented via AOP aspect

## Consequences

Positive:
- Uniform behavior across all providers
- Cascade failures prevented
- Fast partial-response dashboards
- Observable provider health

Negative:
- Per-provider config must be tuned
- Circuit breaker tuning requires runtime data
- Fallback values can mask real outages if too aggressive

## Compliance
- NFR-AVL-003: every external call has timeout/retry/circuit/bulkhead
- Spec § "Resilience contract"
- API Standards § "Read resilience"
