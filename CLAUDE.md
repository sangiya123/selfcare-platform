# CLAUDE.md — Developer Guidance for AI Assistants

This file provides context for AI assistants working on this codebase.

## Project Overview

OMOBIO Selfcare Platform is a configurable, multi-tenant, **multi-industry** selfcare product.
One codebase renders industry-specific experiences for clients (telecom operators,
insurance companies, travel companies, banks, ...) from versioned configuration —
no source forks.

### Terminology (canonical)

| Term | Meaning |
|---|---|
| **Tenant** / **Client** | A business using the OMOBIO platform (Dialog, AIA, ...) |
| **Industry** | The vertical: TELCO, INSURANCE, TRAVEL, BANKING, ... |
| **Tenant type** | The role within the industry: OPERATOR, INSURER, MVNO, AIRLINE, ... |
| **Industry pack** | The per-vertical provider implementation (telco pack, insurance pack) |
| **Industry client** | A specific company under an industry pack (Dialog, AIA) |
| **Integration** | A configured connection to an upstream system (BSS, SMSC, AIA API, ...) |
| **Brand** | The visual identity (logo, colors, fonts) for a client |

## Key Architectural Decisions (ADRs)

See `backend/docs/adrs/` for full ADRs. The critical ones:

- **ADR-001**: One product, no client forks. Client customization via config + industry packs.
- **ADR-002**: React Native + TypeScript for mobile. React + TypeScript for web/admin.
- **ADR-003**: Java 25 + Spring reactive stack for backend microservices.
- **ADR-004**: MongoDB as config source of truth. Compile to immutable runtime manifest at publish time.
- **ADR-006**: Dialog entitlement = primary identity's linked-connection list membership (NOT NIC ownership).
- **ADR-008**: Partial dashboard response. Widgets fail independently; overall deadline returns partial results.
- **ADR-009**: Config cannot execute arbitrary code. Only registered components/actions/connectors.
- **ADR-011**: OPEN — Token policy (42-day access / 7-month refresh) needs security ADR before production.

## Directory Structure

```
selfcare-platform/
├── backend/                  # Java 25 + Spring Boot Maven multi-module
│   ├── platform-common/      # Shared: tenant, adapter registry, feature flags, security, observability
│   ├── api-gateway/          # Single entry point, tenant routing, strangler-fig fallback
│   ├── config-tenant-service/ # Theme/layout/feature-flag resolution, Mongo + Redis
│   ├── customer-identity-service/ # Public customer auth: OTP, OIDC, JWT, JWKS
│   ├── admin-identity-service/   # Internal admin auth: SAML, RBAC
│   ├── account-entitlement-service/ # Account/linked-connection + Dialog entitlement rule
│   ├── dashboard-bff/        # Resilient widget orchestrator with partial response
│   ├── product-service/       # Product/offer catalog + canonical model
│   ├── usage-service/        # Balance/usage/allowance
│   ├── billing-service/       # Bills, documents
│   ├── payment-service/       # Payments, idempotency, reconciliation
│   ├── notification-service/  # Push/SMS/email with pluggable provider adapter
│   ├── content-service/       # CMS/articles/FAQs/localization
│   ├── journey-service/       # Configurable multi-step journey runtime
│   ├── reporting-service/     # Report catalog + async generation
│   ├── ai-gateway/           # AI model gateway + tool permissions + RAG
│   ├── audit-service/        # Immutable audit trail
│   └── insurance-service/    # Insurance BFF — policy, claims, beneficiaries, premiums
├── admin/                    # Selfcare Studio (React + TypeScript)
│   └── selfcare-studio/     # Page builder, journey builder, integration builder, RBAC
├── mobile/                  # Selfcare App (React Native + TypeScript)
│   └── selfcare-app/        # Config SDK, component registry, layout renderer, action engine
├── industry-packs/          # Per-industry provider implementations
│   ├── telco/               # Telco industry pack (connection-centric)
│   │   ├── dialog/          # Dialog (LK) — telco client
│   │   ├── hutch/           # Hutch (LK) — telco client
│   │   └── airtel/          # Airtel (LK) — telco client
│   └── insurance/           # Insurance industry pack (policy-centric)
│       └── aia/             # AIA (multi-country) — insurance client
├── config-schema/           # JSON Schema for config validation + compiler
│   └── schemas/
├── ci/                     # CI/CD pipelines
├── observability/           # Grafana, alerts, runbooks
└── templates/              # ADR, feature, provider, config, runbook templates
```

## Backend Patterns

### Service Structure (follow this pattern for every new service)

```
service/
├── pom.xml                  # Maven module, depends on platform-common
├── src/main/java/com/omobio/{service}/
│   ├── {Service}Application.java
│   ├── config/             # Spring configuration
│   ├── domain/             # Entities, value objects
│   ├── repository/        # Data access (JPA, Mongo, Redis)
│   ├── service/            # Business logic
│   ├── web/                # REST controllers
│   ├── web/dto/            # Request/response DTOs
│   └── client/             # Downstream service clients
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/      # Flyway migrations
└── src/test/java/          # Unit + integration tests
```

### Adding a New Service

1. Copy `backend/platform-common` as the starting module reference
2. Create Maven module under `backend/`
3. Add to root `backend/pom.xml` `<modules>`
4. Extend `platform-common` dependency
5. Define provider adapter interface (see `ApiAdapter` pattern in platform-common)
6. Register adapter via `@PostConstruct` config class
7. Add route in `api-gateway/src/main/resources/application.properties`
8. Add Helm values under `backend/deploy/helm/values/`
9. Add stage to `backend/Jenkinsfile`

### Provider Pattern

Every external system call follows the **adapter pattern**, with a
**canonical interface** per industry. Telco and insurance are different
domains — they have different interfaces.

The platform has 21 canonical provider interfaces in `platform-common/src/main/java/com/omobio/platform/common/adapter/`:

**Identity & auth (per industry)**
- `AuthProvider` — login / refresh / logout (telco + insurance)
- `InsuranceProvider` — policies, claims, beneficiaries, premiums
- `MobileNumberProvider` (telco)
- `ConnectionProvider` (telco)
- `SimLifecycleProvider` (telco)
- `LocationProvider` — cell tower lookup, geocoding, branch locator

**Catalog & commerce (cross-industry)**
- `ProductCatalogProvider` — product/offer catalog
- `PricingProvider` — active price, price matrix, tax breakdown
- `PromotionProvider` — promotion validation + application
- `LoyaltyProvider` — points / tier / rewards
- `RechargeProvider` (telco)
- `UsageProvider` (telco) — usage records, balance, allowance
- `BillingProvider` — bills, invoices, statements
- `PaymentProvider` — gateway calls
- `NotificationChannelProvider` — push / SMS / email / in-app
- `EligibilityProvider` (telco) — can-this-customer-do-X
- `ProfileProvider` — customer profile / KYC
- `ActivationProvider` (telco) — SIM activation, port-in
- `AiProvider` — chat completions, embeddings, RAG
- `UrlAllowlist` (security) — SSRF protection for outbound URLs

## Legacy Code Reference

```java
// 1. Define canonical interface (per industry)
public interface BalanceProvider extends ApiAdapter {     // TELCO
    BalanceResponse getBalance(String connectionId, String tenantId);
}

public interface InsuranceProvider extends ApiAdapter {  // INSURANCE
    List<InsurancePolicy> getPolicies(String tenantId, String customerId);
    ClaimSubmitResult submitClaim(String tenantId, String policyId, ClaimSubmission s);
    // ...
}

// 2. Implement for a specific client (Dialog, AIA, ...)
@Component
@RegisterAdapter("${omobio.tenant.default-id:dialog-lk}")
public class DialogBalanceProvider implements BalanceProvider {
    // Calls Dialog's real BSS API
    // Reads URL/credentials from TenantConfigurationService at runtime
}

// 3. Inject and use via registry
@RequiredArgsConstructor
public class BalanceService {
    private final ApiAdapterRegistry<BalanceProvider> registry;

    public BalanceResponse getBalance(String connectionId, String tenantId) {
        return registry.getProvider(tenantId).getBalance(connectionId, tenantId);
    }
}
```

### Dashboard BFF Pattern

Widget fan-out with resilience:

```java
// Concurrent widget execution with deadline
Flux<WidgetResult> widgets = Flux.fromIterable(configuredWidgets)
    .flatMap(widget -> callWidgetProvider(widget)
        .timeout(Duration.ofMillis(widget.getTimeout()))
        .onErrorReturn(widget.getFallback())
        .map(result -> WidgetResult.success(widget.getId(), result))
        .defaultIfEmpty(WidgetResult.timeout(widget.getId()))
    )
    .takeUntil(Duration.ofMillis(overallDeadline));

return widgets.collectList();
```

## Frontend Patterns

### Mobile (React Native)

- **Config SDK**: fetches compiled manifest from Config Service; caches locally
- **Component Registry**: maps `componentId` to actual React Native component
- **Layout Renderer**: recursively renders sections/widgets from manifest
- **Action Engine**: executes `NAVIGATE`, `CALL_API`, `START_JOURNEY`, etc.
- **Theme Engine**: resolves design tokens from tenant config

### Admin Portal (React)

- Three-pane layout: component palette / canvas / property inspector
- Uses React 18+, TypeScript 5+, modern component patterns
- Do NOT follow the old MUI v4 patterns from `microservices/admin-portal/`

## Data Storage

| Data | Store |
|---|---|
| Config (theme/layout/journey) | MongoDB |
| Compiled runtime manifest | Cached in service process memory |
| Auth session | MySQL InnoDB (durable) + Redis AUTH (hot cache) |
| Profile/connections | MySQL InnoDB (Kafka-fed read model) |
| Compact entitlement | Redis AUTH (invalidated on Kafka change) |
| Product/offer read models | MySQL InnoDB (materialized views) |
| Audit trail | MySQL InnoDB (append-only) |
| Events | Kafka |
| Assets | Object storage + CDN (Mongo stores metadata) |
| Logs | ELK (structured, PII masked) |

## Tenant Isolation

Every request carries `X-Tenant-Id` header. `TenantResolverFilter` in platform-common extracts it.
All services are tenant-aware. No cross-tenant data access.

## Important Rules

1. **Never write `if (tenantId.equals("dialog-lk"))` in core services.** Use the provider/adapter pattern. If you need different behavior for telco vs insurance, dispatch by `industry` on the tenant config — not by tenant ID.
2. **Never hardcode secrets.** Use secret manager references or Kubernetes secrets.
3. **Never log PII.** Mask sensitive fields before logging.
4. **Never block the dashboard for a slow widget.** Use per-widget timeout + circuit breaker.
5. **Config changes need approval workflow.** Draft → Validate → Preview → Approve → Publish → Rollback.
6. **Token policy (ADR-011) is unresolved.** Do not implement 42-day access token without security review.
7. **Use industry-neutral language in core code.** The platform is multi-industry. "Tenant", "client", "brand" are universal. "Operator", "MSISDN", "subscriber" are telco-only and should stay inside the telco industry pack. "Policyholder", "policy", "premium" are insurance-only and should stay inside the insurance industry pack.
8. **Industry pack boundaries are hard.** Cross-industry logic goes in `platform-common`. Industry-specific logic goes in the corresponding industry pack. Never reach across.

## Automation (CI / CD / Security / Deployment)

The platform ships with full automation across three CI systems, multiple security
scanners, and a multi-environment deployment pipeline. Everything below is
expected to be in place; do not delete or weaken these workflows.

### CI/CD Pipelines (multi-CI)

| File | Purpose |
|---|---|
| `ci/github-actions/backend-ci.yml` | Backend compile + lint + test matrix per service |
| `ci/github-actions/admin-ci.yml` | Admin portal (TypeScript / Vitest) |
| `ci/github-actions/mobile-ci.yml` | Mobile (React Native / Jest) |
| `ci/github-actions/pr-verify.yml` | **PR merge gate** — change-detection, backend/admin/mobile tests, conformance, license, secrets, PR comment |
| `ci/github-actions/sonar.yml` | SonarQube quality gate (Java, TypeScript) — JaCoCo + LCOV |
| `ci/github-actions/owasp-dependency-check.yml` | SCA — backend Maven, admin npm, mobile npm; SARIF upload; fail on CVSS ≥ 7 |
| `ci/github-actions/security-scan.yml` | Trivy filesystem, Gitleaks secret scan, Semgrep SAST |
| `ci/github-actions/dast-zap.yml` | OWASP ZAP baseline + API scan (nightly 02:00 UTC) |
| `ci/github-actions/performance.yml` | k6 performance suite (smoke, load, stress, spike, soak) |
| `ci/github-actions/release-deploy.yml` | Release pipeline — 18-service build, cosign keyless signing, SLSA L3 provenance, multi-env deploy with blue/green |
| `ci/github-actions/sit.yml` | **SIT** — system integration testing against deployed env (preflight + conformance + API automation + DAST + synthetic probes) |
| `ci/github-actions/uat.yml` | **UAT** — user acceptance with manual sign-off gate (e2e + mobile + admin regression + accessibility + perf smoke) |
| `ci/github-actions/canary-deploy.yml` | **Canary** — 5% → 25% → 50% → 100% with SLO-based auto-rollback |
| `ci/github-actions/monitoring-alerts.yml` | **Monitoring** — synthetic probes (15m), daily SLO snapshot, alertmanager routing tests, Grafana dashboard deploy, Sentry verification |
| `ci/gitlab/.gitlab-ci.yml` | GitLab CI mirror of the GitHub pipeline |
| `ci/jenkins/Jenkinsfile` | Jenkinsfile with parallel lint/scan/test/build/deploy matrix |

### Code Quality (Maven)

Configured in `backend/pom.xml` via four profiles:

- **`ci`** — full enforcement: `verify` with Checkstyle, SpotBugs, PMD, OWASP, JaCoCo check
- **`quality-checks`** — Checkstyle, SpotBugs, PMD only
- **`owasp-scan`** — OWASP dependency-check
- **`sonar`** — SonarQube scan

Static analysis config:

- `backend/checkstyle.xml` — 140-char lines, 4-space indent, naming, Javadoc, security-sensitive rules
- `backend/checkstyle-suppressions.xml` — generated code, migrations, lombok

Coverage: JaCoCo 60% line-coverage threshold enforced in PR gate (`ci/github-actions/pr-verify.yml`).

### Security Tooling

| Tool | Purpose | File / Config |
|---|---|---|
| **Checkstyle** | Java style + security (no System.out, no printStackTrace) | `backend/checkstyle.xml` |
| **SpotBugs** | Java bug patterns | `pom.xml` plugin |
| **PMD** | Java code analysis | `pom.xml` plugin |
| **OWASP dependency-check** | SCA, NVD feed, fail CVSS ≥ 7 | `ci/github-actions/owasp-dependency-check.yml` |
| **Trivy** | Filesystem + image scan | `ci/github-actions/security-scan.yml` |
| **Gitleaks** | Secret detection | `ci/github-actions/security-scan.yml` |
| **Semgrep** | SAST for TS / JS | `ci/github-actions/security-scan.yml` |
| **npm audit** | Admin / mobile dependency audit | `ci/github-actions/owasp-dependency-check.yml` |
| **OWASP ZAP** | DAST baseline + API | `ci/github-actions/dast-zap.yml` (nightly) |
| **Cosign** | Keyless image signing (GitHub OIDC) | `ci/github-actions/release-deploy.yml` |
| **SLSA L3** | Provenance attestation | `ci/github-actions/release-deploy.yml` |
| **CycloneDX** | SBOM generation | `pom.xml` plugin |

### Multi-Environment Deployment

| Env | Auto / Manual | Strategy |
|---|---|---|
| dev | auto on `main` | Helm install/upgrade |
| stg | auto on `main` | Helm install/upgrade |
| reg | **manual** | Helm install/upgrade |
| prod | **manual** | Blue/green: 10% → 50% → 100% traffic shift |

Per-service values: `backend/deploy/helm/values/{dev,stg,reg,prod}.yaml`

### Unit Test Coverage

All under-tested services have been extended with comprehensive JUnit 5 + Mockito
test classes. See `backend/*/src/test/java/com/omobio/*/` for tests covering:

- `api-gateway`: 6 test classes — WAF, tenant routing, JWT relay, error handler, key resolver, rate-limit exclusion
- `audit-service`: 2 test classes — record / search / CSV export
- `approval-service`: 1 test class — workflow transitions, expiry sweep
- `account-entitlement-service`: 2 test classes — Redis + DB entitlement, account/connection CRUD
- `content-service`: 1 test class — article/FAQ/banner CRUD with cache invalidation
- `config-tenant-service`: 1 test class — tenant CRUD with Redis cache
- `journey-service`: 1 test class — definition lifecycle + instance state machine
- `notification-service`: 1 test class — multi-channel send with provider fallback
- `insurance-service`: 1 test class — provider-first with MongoDB cache fallback
- `customer-identity-service`: GdprService — consent + data erasure (Article 7, 17, 20)
- `platform-common`: CryptoUtils — SHA-256, HMAC, OTP, masking, constant-time equality

### Mobile Security (React Native)

| File | Purpose |
|---|---|
| `mobile/selfcare-app/src/services/SecureStorage.ts` | Keychain (iOS) / EncryptedSharedPreferences (Android) wrapper with biometric-bound option |
| `mobile/selfcare-app/src/services/BiometricAuth.ts` | Face ID / Touch ID / fingerprint prompt, server-validated signatures |
| `mobile/selfcare-app/src/services/ConsentManager.ts` | GDPR consent capture (Article 7), Article 17 erasure, Article 20 export |
| `mobile/selfcare-app/src/utils/crypto.ts` | SHA-256, HMAC, AES-256-GCM, HKDF, base64url, TOTP, secure random |
| `mobile/selfcare-app/tests/security/SecureStorage.test.ts` | 16 security tests (storage, crypto, consent) |

Mobile secrets are stored exclusively in SecureStorage. MMKV is only for
non-sensitive fast reads (theme preference, last-seen tab, etc.). PII
never touches MMKV, AsyncStorage, or any non-encrypted file.

### GDPR / Privacy (Backend)

| File | Purpose |
|---|---|
| `customer-identity-service/.../domain/ConsentRecord.java` | Immutable consent audit record (per purpose / version / source / IP) |
| `customer-identity-service/.../domain/DataErasureRequest.java` | Right-to-be-forgotten audit (user ID is hashed; original not recoverable) |
| `customer-identity-service/.../repository/ConsentRecordRepository.java` | Active vs. superseded consent queries |
| `customer-identity-service/.../repository/DataErasureRequestRepository.java` | Erasure lifecycle (PENDING → IN_PROGRESS → COMPLETED / FAILED) |
| `customer-identity-service/.../service/GdprService.java` | Consent capture, erasure, data export, Kafka fan-out |
| `customer-identity-service/.../web/GdprController.java` | REST: POST /api/v1/customer/consent, /data-erasure, GET /data-export |
| `customer-identity-service/src/test/.../GdprServiceTest.java` | 12 tests — consent supersession, erasure lifecycle, hashing |
| `platform-common/.../security/CryptoUtils.java` | SHA-256, HMAC, secure random, OTP, constant-time equality, PII masking |
| `platform-common/.../security/UrlAllowlist.java` | SSRF protection for outbound URLs (operator BSS, AI provider) |
| `platform-common/.../security/UrlAllowlistTest.java` | Allow-list and private-IP rejection tests |

GDPR endpoints emit Kafka events on `identity.user.erasure` so that
downstream services (account, audit, notification, payment) can perform
their own data erasure. The erasure record itself is retained (with
hashed user ID) for the legally-required "do not re-register"
obligation.

### SSRF Protection

- `UrlAllowlist` validates outbound URLs (configurable per tenant)
- Two layers: hostname allowlist + private/reserved IP rejection
- Used by every provider adapter before making a network call
- Built into WAF (api-gateway) for inbound SSRF too

## Planning Documents

All planning docs are in the parent `Planning doc/` folder. Key files:
- `OMOBIO_Global_Selfcare_ALL_MARKDOWN_DOCUMENTS/03_architecture/` — architecture docs
- `OMOBIO_Global_Selfcare_ALL_MARKDOWN_DOCUMENTS/09_roadmap_migration/` — implementation roadmap
- `OMOBIO_SC_FINAL_v2/docs/` — consolidated implementation pack (short filenames)
