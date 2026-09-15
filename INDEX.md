# Selfcare Platform — Index

## Top-Level Files

| File | Purpose |
|---|---|
| [README.md](README.md) | Overview, architecture, quick start |
| [START.txt](START.txt) | Phased development plan from planning docs |
| [CLAUDE.md](CLAUDE.md) | Developer guidance for AI assistants |
| [Jenkinsfile](Jenkinsfile) | CI/CD pipeline (Active Choices, multi-service) |
| [docker-compose.yml](docker-compose.yml) | Full local stack (18 services + infra) |
| [docs/IMPLEMENTATION_SUMMARY.md](docs/IMPLEMENTATION_SUMMARY.md) | Detailed status of what was built |
| [docs/DYNAMIC_CONFIG.md](docs/DYNAMIC_CONFIG.md) | DB-backed config model (client_integrations, env:/secret:, AI routing) |
| [docs/DIALOG_APP.md](docs/DIALOG_APP.md) | Dialog end-to-end walkthrough (seeds, runtime creds, docker run) |

## Backend Microservices

Runtime stack: Java 25 + Spring Boot 4.1.1, Maven multi-module. Compose maps
each service to a unique host port (8080–8097); infra on 3306/27017/6379/9092.

| Service | Compose Port | Purpose | Status |
|---|---|---|---|
| `api-gateway` | 8080 | Single entry, tenant routing, WAF, rate limits, circuit breakers | ✅ Complete |
| `config-tenant-service` | 8081 | Theme/layout/journey/integration config, Mongo + Redis, signed manifest | ✅ Complete |
| `customer-identity-service` | 8082 | Customer auth: OTP, OIDC, JWT, JWKS, sessions, GDPR | ✅ Complete |
| `admin-identity-service` | 8083 | Admin auth: SAML, RBAC, MFA, user management | ✅ Complete |
| `account-entitlement-service` | 8084 | Account/connection + ADR-006 linked-connection rule | ✅ Complete |
| `dashboard-bff` | 8085 | Resilient widget orchestrator with partial response (ADR-008) | ✅ Complete |
| `product-service` | 8086 | Product/offer/catalog read models + canonical model | ✅ Complete |
| `usage-service` | 8087 | Balance/usage/allowance | ✅ Complete |
| `billing-service` | 8088 | Bills, invoices, documents | ✅ Complete |
| `payment-service` | 8089 | Payments, idempotency, reconciliation | ✅ Complete |
| `notification-service` | 8090 | Push/SMS/email channels | ✅ Complete |
| `content-service` | 8091 | CMS, articles, FAQs, banners | ✅ Complete |
| `journey-service` | 8092 | Multi-step journey runtime | ✅ Complete |
| `reporting-service` | 8093 | Report catalog + async generation | ✅ Complete |
| `ai-gateway` | 8094 | AI model gateway, RAG, tools, moderation (DB-driven providers) | ✅ Complete |
| `audit-service` | 8095 | Immutable audit trail | ✅ Complete |
| `insurance-service` | 8096 | Insurance BFF (policies, claims, beneficiaries, premiums) | ✅ Complete |
| `approval-service` | 8097 | Config change approval workflow (draft → validate → preview → approve → publish) | ✅ Complete |

All 18 services are fully implemented (domain logic + web layer + tests).
See [docs/IMPLEMENTATION_SUMMARY.md](docs/IMPLEMENTATION_SUMMARY.md).

### Important runtime notes

- **DB name mapping** (MySQL `selfcare_*` databases, applied via
  `deploy/local/mysql-init/01-schema.sql`): identity → `selfcare_identity`,
  admin → `selfcare_admin`, account-entitlement/billing/usage/product/
  insurance → `selfcare_core`, payment → `selfcare_payment`,
  audit → `selfcare_audit`, notification → `selfcare_notification`,
  approval → `selfcare_approval`, reporting → `selfcare_reporting`.
- **Mongo URI env var is `SPRING_MONGODB_URI`** (Boot 4.1 uses
  `spring.mongodb.*`, not `spring.data.mongodb.*`). Redis stays
  `spring.data.redis`. Compose sets both correctly.
- **Servlet vs reactive:** services with BOTH `spring-boot-starter-web` and
  `spring-boot-starter-webflux` must set `spring.main.web-application-type:
  servlet` (this is the MongoTenantValidator 401 root cause). `api-gateway`
  is deliberately reactive (Spring Cloud Gateway).
- **Kafka:** Boot 4.1 ships no Kafka auto-config. `usage/notification/
  account-entitlement` have `KafkaConfig`, `payment/customer-identity` have
  `KafkaProducerConfig`, `audit-service` has `AuditKafkaConfig`.

## Frontend

| Path | Purpose | Status |
|---|---|---|
| [mobile/selfcare-app/](mobile/selfcare-app/) | React Native + TypeScript mobile kernel (Config SDK, registry, layout renderer, action engine) | ✅ ConfigSDK + v2 server-driven kernel (28 passing tests) |
| [admin/selfcare-admin/](admin/selfcare-admin/) | React + TypeScript admin portal (11 pages incl. Page Builder, Theme, Journey, Integration Builder, AI Studio) | ✅ Complete |

## Industry Packs

The platform is multi-industry. Each vertical gets its own industry pack under
`industry-packs/`. Each client/tenant (a business using selfcare) is bound to
exactly one industry pack, which determines the provider beans, terminology,
and features available for that tenant. Provider bindings are DB-driven
(`tenant_configs.providerBindings`), never hardcoded by tenant ID.

### Telco industry pack — `industry-packs/telco/`

| Client | Path | Status |
|---|---|---|
| Dialog (LK) | [industry-packs/telco/dialog/](industry-packs/telco/dialog/) | ✅ Full providers (auth, balance, usage, bill, recharge, payment, notification, product) |
| Hutch (LK) | [industry-packs/telco/hutch/](industry-packs/telco/hutch/) | ✅ Full providers |
| Airtel (LK) | [industry-packs/telco/airtel/](industry-packs/telco/airtel/) | ✅ Full providers |

### Insurance industry pack — `industry-packs/insurance/`

| Client | Path | Status |
|---|---|---|
| AIA (multi-country) | [industry-packs/insurance/aia/](industry-packs/insurance/aia/) | ✅ Policy / Claims / Beneficiaries / Premiums |

### Adding a new client

For telco: copy `industry-packs/telco/dialog/`; for insurance:
`industry-packs/insurance/aia/`. A new country under the same insurer is just
config — append to the Mongo seed + `ClientIntegrationSeeder` (dev profile).
No core code change. See [docs/INSURANCE.md](docs/INSURANCE.md).

## Config Schemas

| Path | Purpose |
|---|---|
| config-schema/schemas/*.json | 9 JSON Schemas (layout, theme, journey, integration, feature flag, …) |
| config-schema/schemas/layout-document.schema.json | Layout document validation |
| config-schema/schemas/integration-config.schema.json | Integration (client_integrations) validation |

## DevOps

| Path | Purpose |
|---|---|
| [backend/deploy/helm/](backend/deploy/helm/) | Helm charts (microservice + per-operator values) |
| [Jenkinsfile](Jenkinsfile) | Multi-service CI/CD (Active Choices Option 1) |
| [docker-compose.yml](docker-compose.yml) | Full local stack (MySQL 8, Mongo, Redis, Kafka, 18 services, Prometheus, Grafana) |
| [deploy/local/mysql-init/](deploy/local/mysql-init/) | MySQL schema seed (`01-schema.sql`, 8 databases) |
| [deploy/local/mongo-init/](deploy/local/mongo-init/) | Mongo seed (`01-tenant-dialog.js`, collections + client_integrations) |

## Observability

| Path | Purpose |
|---|---|
| [observability/grafana/dashboards/dashboard-bff.json](observability/grafana/dashboards/dashboard-bff.json) | Grafana dashboard for Dashboard BFF |
| [observability/prometheus-alerts.yaml](observability/prometheus-alerts.yaml) | Prometheus alerting rules |
| [observability/runbooks/](observability/runbooks/) | SRE runbooks |

## Architecture Decision Records

| Path | Purpose |
|---|---|
| [docs/adrs/ADR-001-no-fork.md](docs/adrs/ADR-001-no-fork.md) | One product, zero operator forks |
| [docs/adrs/](docs/adrs/) | 12 ADRs total (ADR-011 token policy open) |

## Source Code Stats

- **Backend services**: 18 complete (+ platform-common library)
- **Frontend**: mobile RN kernel (28 passing tests) + admin Studio 11 pages
- **Industry packs**: 3 telco clients + AIA (6 countries)
- **Config schemas**: 9
- **Database seeds**: MySQL (8 DBs) + Mongo (Dialog tenant, 8 client_integrations)
- **Observability**: Grafana dashboards, Prometheus alerts, runbooks