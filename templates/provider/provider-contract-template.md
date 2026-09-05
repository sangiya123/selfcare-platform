# Provider Contract — <operator>/<industry>/<capability>

| Field | Value |
|---|---|
| **Provider ID** | P-NNNN |
| **Operator / client** | e.g. Dialog, Hutch, Airtel, AIA, Allianz |
| **Industry pack** | telco, insurance, travel, banking |
| **Capability** | e.g. balance, usage, billing, payment, claims |
| **Owner** | name + email |
| **Status** | DRAFT / ACCEPTED / IMPLEMENTED / DEPRECATED |
| **Version** | semver of the contract (changes break consumers) |

## Purpose

What business capability does this provider expose? One sentence.

> Example: "Retrieve the postpaid balance for a given connection in
> Dialog's BSS."

## Canonical Interface

Reference the canonical Java interface in `platform-common`. The
canonical interface is the contract — implementations vary per operator.

```java
public interface BalanceProvider extends ApiAdapter {
    BalanceResponse getBalance(String tenantId, String connectionId);
}
```

## Methods

### `<methodName>`

- **Signature:** …
- **Description:** …
- **Input parameters:** …
- **Output:** …
- **Errors:**
  - `BAD_REQUEST` (400) — invalid input
  - `NOT_FOUND` (404) — connection does not exist
  - `UPSTREAM_TIMEOUT` (504) — operator BSS unreachable
  - `UPSTREAM_ERROR` (502) — operator returned unexpected error
- **Idempotency:** required / not required
- **Cache TTL:** 60s (operator data changes mid-cycle)
- **SLO:** p99 < 800ms

## Wire / Protocol

- **Transport:** REST / SOAP / gRPC / JDBC / Mongo / SFTP / Kafka
- **Auth:** OAuth2 client credentials / mTLS / API key / certificate
- **Base URL:** `https://bss.<operator>.com/...` (configurable per tenant)
- **Credentials location:** Kubernetes secret `omobio-<tenant>-<env>-<capability>`
- **TLS version:** 1.2 minimum
- **Versioning rules:** semver, breaking changes require new major version

## Data Model

JSON schema, XSD, or table definitions. Include example payload.

```json
{
  "connectionId": "94771123456",
  "tenantId": "dialog-lk",
  "balance": {
    "outstanding": 1250.50,
    "currency": "LKR",
    "dueDate": "2026-09-15"
  }
}
```

## Resilience

- **Timeout:** 5s
- **Retry policy:** 3 retries, exponential backoff (200ms, 1s, 5s), jitter
- **Circuit breaker:** 10 failures in 30s → open for 60s
- **Bulkhead:** max 100 concurrent calls
- **Fallback:** cached read model in MongoDB (last 24h)

## Mapping (per industry)

| Operator | Implementation | Notes |
|---|---|---|
| Dialog | `DialogBalanceProvider` | REST → BSS, OAuth2 |
| Hutch | `HutchBalanceProvider` | REST → BSS, mTLS |
| Airtel | `AirtelBalanceProvider` | REST → Airtel gateway |
| AIA | (n/a — insurance) | |
| Allianz | (n/a — insurance) | |

## Testing

- **WireMock stubs:** location in `tests/conformance/wiremock/<operator>/<capability>/`
- **Contract test:** `tests/conformance/.../ProviderAdapterTest.java`
- **Resilience test:** simulate 5xx, timeout, slow response, malformed payload
- **Live smoke:** canary cohort of 5 users per operator per environment

## Observability

- **Metrics:** `provider.<operator>.<capability>.latency` (histogram),
  `provider.<operator>.<capability>.error_rate` (counter)
- **Logs:** structured, include correlationId, tenantId, traceId
- **Traces:** OTEL spans for the entire call chain
- **Alerts:** error rate > 5%, p99 > 1.5s

## Security

- **PII handling:** mask MSISDN in logs (`+947****56`), redact full NIC
- **Credentials rotation:** every 90 days, automated via Vault / KMS
- **Audit log:** every successful call written to `audit-service` with
  correlationId, action=`PROVIDER_CALL`
- **Rate limit:** 100 rps per operator, 429 → exponential backoff

## Rollout

- Implement against WireMock stubs first
- Test against operator sandbox
- Deploy to dev → stg → reg → prod (canary 5%)
- Validate per-call latency and error rate against SLO

## Change Management

- **Breaking changes:** require new contract version + coordinated
  rollout (operator + downstream consumers)
- **Deprecation:** minimum 90-day notice; mark `@Deprecated` in code;
  document migration path
- **Owner:** must respond to incidents within 1 business day
