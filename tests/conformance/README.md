# selfcare Conformance Test Suite

Comprehensive automated conformance tests for the Selfcare Platform. This
suite validates that a deployment or new client is correctly integrated with the
platform by exercising the full API surface across all 11 testing layers defined
in [Test Strategy §12.2](../docs/CONFORMANCE_SUITE.md).

## What is tested

| # | Category | What it checks | File |
|---|---|---|---|
| 1 | **Tenant isolation** | Tenant A cannot access Tenant B's data | `tenant/TenantIsolationTest.java` |
| 2 | **Auth flow** | Login, refresh, logout, OTP verification, token claims | `auth/AuthFlowTest.java` |
| 2 | **Refresh token replay** | Reuse of an old refresh token triggers full session revocation | `auth/RefreshTokenReplayTest.java` |
| 3 | **API contracts** | All endpoints return expected shapes, headers, status codes | `api/ApiContractTest.java` |
| 4 | **Config schema** | Themes, layouts, manifests compile and respect ETag/version | `config/ConfigSchemaTest.java` |
| 5 | **Provider adapters** | All providers return correct shapes, timeouts, errors translate | `provider/ProviderAdapterTest.java` |
| 6 | **Journey simulation** | Multi-step end-to-end user journeys | `journey/JourneySimulationTest.java` |
| 7 | **Dashboard partial response** | Slow/failing widgets don't break the dashboard | `dashboard/PartialResponseTest.java` |
| 8 | **Payment idempotency** | Duplicate requests with same key return original result | `payment/IdempotencyTest.java` |
| 8 | **Step-up auth** | Sensitive operations require re-authentication | `payment/StepUpTest.java` |
| 9 | **AI tool permissions** | Read/write/sensitive tools follow permission model | `ai/ToolPermissionTest.java` |
| 9 | **RAG isolation** | RAG knowledge base is tenant-scoped | `ai/RAGIsolationTest.java` |
| 10 | **Observability** | Correlation ID propagation, W3C trace context, timing headers | `observability/CorrelationIdTest.java` |
| 11 | **Rate limiting** | Per-user, per-tenant, per-IP, per-category limits | `security/RateLimitTest.java` |
| 11 | **Idempotency key** | Format, scope, TTL, replay protection | `security/IdempotencyKeyTest.java` |

## Layout

```
tests/conformance/
├── pom.xml                              # Maven project (TestNG, RestAssured, WireMock, Testcontainers)
├── README.md                            # this file
└── src/test/
    ├── java/com/selfcare/conformance/
    │   ├── TestRunner.java              # ISuiteListener / ITestListener; writes JSON summary
    │   ├── tenant/TenantIsolationTest.java
    │   ├── auth/
    │   │   ├── AuthFlowTest.java
    │   │   └── RefreshTokenReplayTest.java
    │   ├── api/ApiContractTest.java
    │   ├── config/ConfigSchemaTest.java
    │   ├── provider/ProviderAdapterTest.java
    │   ├── journey/JourneySimulationTest.java
    │   ├── dashboard/PartialResponseTest.java
    │   ├── payment/
    │   │   ├── IdempotencyTest.java
    │   │   └── StepUpTest.java
    │   ├── ai/
    │   │   ├── ToolPermissionTest.java
    │   │   └── RAGIsolationTest.java
    │   ├── observability/CorrelationIdTest.java
    │   ├── security/
    │   │   ├── RateLimitTest.java
    │   │   └── IdempotencyKeyTest.java
    │   └── utils/                        # shared test helpers
    │       ├── TestConfig.java          # base URL, tenant, RestAssured config
    │       ├── AuthHelper.java          # OTP login + token bundle
    │       ├── WireMockManager.java     # shared WireMock server
    │       └── TestDataFactory.java     # unique IDs, request bodies
    └── resources/
        ├── testng.xml                   # test grouping + parallelism
        └── wiremock/mappings/
            ├── dialog-balance.json
            └── aia-policies.json
```

## Running

### Local (against running platform on localhost:8080)

```bash
cd tests/conformance
mvn test
```

### Against a deployed environment

```bash
export SELFCARE_BASE_URL=https://staging.selfcare.io
export SELFCARE_TENANT=dialog-lk
export SELFCARE_ADMIN_TENANT=aia-lk

mvn -f tests/conformance/pom.xml test -P stg \
  -Dselfcare.base-url=$SELFCARE_BASE_URL \
  -Dselfcare.tenant=$SELFCARE_TENANT \
  -Dselfcare.admin-tenant=$SELFCARE_ADMIN_TENANT
```

### Run only one group (faster feedback)

```bash
# Tenant isolation only
mvn -f tests/conformance/pom.xml test -P tenant-tests

# Auth only
mvn -f tests/conformance/pom.xml test -P auth-tests

# Security only
mvn -f tests/conformance/pom.xml test -P security-tests
```

### In CI

```yaml
# .github/workflows/conformance.yml
- name: Run conformance suite
  run: |
    mvn -B -f tests/conformance/pom.xml test \
        -Dselfcare.base-url=${{ secrets.STAGING_URL }} \
        -Dselfcare.tenant=${{ secrets.STAGING_TENANT }} \
        -Dselfcare.admin-tenant=${{ secrets.STAGING_ADMIN_TENANT }}
```

## Test design principles

Every test in this suite follows the same four rules:

1. **Self-contained** — no shared state between tests. Each test logs in its own
   session and uses unique identifiers.
2. **Meaningful test data** — values look like real customers (Sri Lankan MSISDNs,
   realistic premium amounts, AIA policy IDs), not `foo`/`bar`.
3. **Clear assertions with messages** — every `assertThat(...).as("...")` explains
   what should be true and why a failure matters.
4. **Descriptive failure messages** — `@Test(description = "...")` produces
   reports that read like documentation.

When a downstream is stubbed (via WireMock), the test still records what it was
trying to validate so a failure tells you *which* contract broke.

## Test groups

Tests are tagged with one or more `@Test(groups = {...})` to support targeted runs:

| Group | Coverage |
|---|---|
| `tenant-isolation` | Cross-tenant access, header validation |
| `auth-flow` | OTP, refresh, sign-out |
| `replay-detection` | Refresh token reuse |
| `api-contract` | Envelope shape, headers, status codes |
| `config-schema` | Layout, theme, manifest validation |
| `provider-adapter` | Downstream provider behavior |
| `journey-simulation` | Multi-step E2E |
| `partial-response` | BFF widget isolation |
| `payment` | Idempotency, step-up |
| `ai-governance` | Tool permissions, RAG isolation |
| `observability` | Correlation ID, tracing |
| `security` | Rate limit, idempotency, isolation |
| `e2e` | End-to-end flows |
| `resilience` | Timeouts, circuit breakers |
| `idempotency` | Idempotency keys |
| `step-up` | Step-up authentication |
| `rate-limiting` | Rate limit enforcement |
| `ai-permissions` | AI tool permissions |
| `rag-isolation` | RAG tenant scoping |
| `correlation-id` | Correlation ID propagation |

## Reports

After a run, three artifacts are produced:

| Artifact | Location | Purpose |
|---|---|---|
| HTML report | `target/surefire-reports/index.html` | Human-readable, drill down into failures |
| JUnit XML | `target/surefire-reports/TEST-*.xml` | CI ingestion (GitHub Actions, Jenkins, GitLab) |
| JSON summary | `target/conformance-summary.json` | Dashboard / trend tracking |

The JSON summary is emitted by `TestRunner` and contains:

```json
{
  "generatedAt": "...",
  "baseUrl": "...",
  "defaultTenant": "dialog-lk",
  "results": [
    { "suite": "...", "class": "...", "method": "...", "status": "PASS|FAIL|SKIPPED",
      "durationMs": 1234, "failure": "..." }
  ],
  "totals": { "passed": N, "failed": N, "skipped": N }
}
```

## Mandatory tests for new clients

Before a tenant can go live, the following groups **must** all pass against the
target environment:

- [ ] `tenant-isolation` (TenantIsolationTest)
- [ ] `auth-flow` and `replay-detection` (AuthFlowTest, RefreshTokenReplayTest)
- [ ] `provider-adapter` (ProviderAdapterTest — for each provider adapter)
- [ ] `payment` (IdempotencyTest, StepUpTest) — only if multi-connection or step-up applies
- [ ] `config-schema` (ConfigSchemaTest)
- [ ] `partial-response` (PartialResponseTest)
- [ ] `security` (RateLimitTest, IdempotencyKeyTest)
- [ ] `ai-governance` (ToolPermissionTest, RAGIsolationTest) — only if AI features are exposed
- [ ] `observability` (CorrelationIdTest)

## Adding a new test

1. Pick the right package (or create one) and add a new `*Test.java`.
2. Annotate with `@Test(groups = {"...", "..."})` and a clear `description`.
3. Use `AuthHelper.login(...)` for auth, `TestDataFactory.*` for unique data,
   and `TestConfig.baseRequestSpec(tenantId)` to keep the boilerplate down.
4. Add the class to `testng.xml` (or rely on the smoke-tests `*` catch-all).
5. Run locally before opening a PR:
   ```bash
   mvn -f tests/conformance/pom.xml test -Dtest=YourNewTest
   ```

## Dependencies

| Library | Why |
|---|---|
| **TestNG 7.8** | Native test grouping, listeners, parallel execution |
| **RestAssured 5.4** | BDD-style HTTP client, JSON path, schema validation |
| **WireMock 3.3** | Stub downstream providers (DialogBSS, AIACore) with JSON files |
| **Testcontainers 1.19** | Spinning up real Redis/MySQL/Kafka when needed |
| **AssertJ 3.24** | Fluent, descriptive assertions |
| **Jackson 2.16** | JSON ↔ Java for fixtures and DTOs |
| **JJWT 0.12** | JWT decoding in AuthFlowTest |
| **Lettuce 6.3** | Direct Redis access for token/session checks |

## Notes

- **WireMock port:** default `8090`. Override with `-Dwiremock.port=...` or
  `SELFCARE_WIREMOCK_PORT`.
- **No auto-start of platform services:** the suite expects the platform to be
  running at `selfcare.base-url`. For local development, start the services
  (`docker compose up` or your IDE) and then run `mvn test`.
- **Idempotent:** each test generates unique identifiers via
  `TestDataFactory` so reruns never collide.
- **Parallel classes:** `testng.xml` runs classes in parallel (4 threads).
  Tests within a class run serially. Override with `-DthreadCount=...`.
