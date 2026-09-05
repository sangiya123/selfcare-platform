# OMOBIO Selfcare Platform — Index

## Top-Level Files

| File | Purpose |
|---|---|
| [README.md](README.md) | Overview, architecture, quick start |
| [START.txt](START.txt) | Phased development plan from planning docs |
| [CLAUDE.md](CLAUDE.md) | Developer guidance for AI assistants |
| [Jenkinsfile](Jenkinsfile) | Multi-service CI/CD pipeline |
| [docker-compose.yml](docker-compose.yml) | Full local stack |
| [docs/IMPLEMENTATION_SUMMARY.md](docs/IMPLEMENTATION_SUMMARY.md) | Detailed status of what was built |

## Backend Microservices

| Service | Port | Purpose | Status |
|---|---|---|---|
| `api-gateway` | 8080 | Single entry, routes, rate limits, circuit breakers | ✅ Complete |
| `config-tenant-service` | 8083 | Theme/layout/journey config, MongoDB | ✅ Domain + Compiler complete |
| `customer-identity-service` | 8081 | Customer auth, sessions, JWT | ✅ Session + Rotation complete |
| `admin-identity-service` | 8082 | Admin auth, SAML, RBAC | 📋 Skeleton |
| `account-entitlement-service` | 8084 | Account/connection, ADR-006 rule | ✅ Complete |
| `dashboard-bff` | 8085 | Resilient widget orchestrator | ✅ Complete |
| `product-service` | 8086 | Product/offer/catalog | 📋 Skeleton |
| `usage-service` | 8087 | Balance/usage/allowance | 📋 Skeleton |
| `billing-service` | 8088 | Bills, invoices, documents | 📋 Skeleton |
| `payment-service` | 8089 | Payments, idempotency, transactions | 📋 Skeleton |
| `notification-service` | 8090 | Push/SMS/email | 📋 Skeleton |
| `content-service` | 8091 | CMS, articles, FAQs | 📋 Skeleton |
| `journey-service` | 8092 | Multi-step journey runtime | 📋 Skeleton |
| `reporting-service` | 8093 | Report catalog, scheduling | 📋 Skeleton |
| `ai-gateway` | 8094 | AI model gateway, RAG, tools | 📋 Skeleton |
| `audit-service` | 8095 | Immutable audit trail | 📋 Skeleton |

## Frontend

| Path | Purpose | Status |
|---|---|---|
| [mobile/selfcare-app/](mobile/selfcare-app/) | React Native + TypeScript mobile kernel | ✅ ConfigSDK, ComponentRegistry, LayoutRenderer, BalanceCard |
| [admin/selfcare-studio/](admin/selfcare-studio/) | React + TypeScript admin portal | ✅ Layout, PageBuilderPage, scaffolding |

## Industry Packs

The platform is multi-industry. Each vertical gets its own industry pack under
`industry-packs/`. Each client/tenant (a business using OMOBIO) is bound to
exactly one industry pack, which determines the provider beans, terminology,
and features available for that tenant.

### Telco industry pack — `industry-packs/telco/`

Telco clients (operators, MVNOs) using this pack:

| Client | Path | Status |
|---|---|---|
| Dialog (LK) | [industry-packs/telco/dialog/](industry-packs/telco/dialog/) | ✅ Theme, navigation, provider bindings, provider stubs |
| Hutch (LK) | [industry-packs/telco/hutch/](industry-packs/telco/hutch/) | ✅ Theme pack |
| Airtel (LK) | [industry-packs/telco/airtel/](industry-packs/telco/airtel/) | ✅ Theme pack |

### Insurance industry pack — `industry-packs/insurance/`

Insurance clients (insurers, brokers) using this pack:

| Client | Path | Status |
|---|---|---|
| AIA (multi-country) | [industry-packs/insurance/aia/](industry-packs/insurance/aia/) | ✅ Policy / Claims / Beneficiaries / Premiums |

### Adding a new client

For telco: copy `industry-packs/telco/dialog/`.
For insurance: copy `industry-packs/insurance/aia/`.

A new country under the same insurer (e.g. AIA Vietnam) is just config —
append two lines to `ClientIntegrationSeeder` and `TenantSeeder`. No code
change. See [docs/INSURANCE.md](docs/INSURANCE.md).

## Config Schemas

| Path | Purpose |
|---|---|
| [config-schema/schemas/layout-document.schema.json](config-schema/schemas/layout-document.schema.json) | JSON Schema for layout document validation |

## DevOps

| Path | Purpose |
|---|---|
| [backend/deploy/helm/](backend/deploy/helm/) | Helm charts (microservice + per-operator values) |
| [Jenkinsfile](Jenkinsfile) | Multi-service CI/CD with Docker + Helm |
| [docker-compose.yml](docker-compose.yml) | Full local stack (MySQL, Mongo, Redis, Kafka, all services, OTel, Prometheus, Grafana) |

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
| [docs/adrs/ADR-011-token-policy.md](docs/adrs/ADR-011-token-policy.md) | Token lifetime policy (proposed) |

## Source Code Stats

- **Backend Java files**: ~50
- **Backend services**: 16 (5 complete, 11 skeletons)
- **Frontend TS/TSX files**: ~10
- **Helm charts**: 1 with per-service + per-operator values
- **Operator packs**: 3 (Dialog, Hutch, Airtel)
- **ADRs**: 2 (with more to come)
- **Observability**: 1 Grafana dashboard, 1 alert ruleset, 1 runbook
