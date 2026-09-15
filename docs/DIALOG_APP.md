# Selfcare — Dialog (LK) End-to-End Walkthrough

Dialog Axiata PLC is the **first configured tenant** (`dialog-lk`, TELCO
industry pack). This document walks the full stack: infra, seeds, services,
AI, and the mobile app — with the concrete runtime values used by the
compose stack.

---

## 1. Prerequisites

- Docker Desktop (v4.x) running, WSL2/EFI backend.
- No Docker? `mvn` (Maven 3.9+) + `java 25` for local builds; Docker recommended.
- Free AI keys (optional) in the shell environment: `ANTHROPIC_API_KEY`,
  `OPENAI_API_KEY`, `GOOGLE_AI_API_KEY` — the seeds reference these via
  `env:` (see `docs/DYNAMIC_CONFIG.md` §3).

## 2. Start the stack

```bash
cd selfcare-platform
docker compose up -d --build          # infra + all 18 services
docker compose ps                      # all services healthy (mysql, mongodb, redis, kafka)

# infra only (useful while developing):
docker compose up -d mongodb redis kafka mysql
```

The Mongo seed (`deploy/local/mongo-init/01-tenant-dialog.js`) runs **once** on
the first boot of the `mongodb` container (via `/docker-entrypoint-initdb.d`)
and provisions all Dialog collections. MySQL seeds (`deploy/local/mysql-init`)
create the 8 `selfcare_*` databases.

### Known infra facts

| Piece | Host port | Credentials |
|---|---|---|
| MongoDB | 27017 | `selfcare` / `Selfcare_M0ng0_Db_Pa55w0rd!2026` (admin auth DB) → db `selfcare_config` |
| Redis | 6379 | password `Selfcare_R3d1s_Pa55w0rd!2026` (AUTH DB) |
| MySQL 8 | 3306 | `selfcare` / `Selfcare_My5ql_Db_Pa55w0rd!2026` (root `Selfcare_My5ql_R00t_Pa55w0rd!2026`) |
| Kafka | 9092 | — (zookeeper 2181) |
| Prometheus / Grafana | 9090 / 3000 | admin/Selfcare_Gr4f4n4_Adm1n_Pa55w0rd!2026 (Grafana) |

> **Re-seeding an existing stack:** seeds are one-shot. To re-run, either
> `docker compose down -v` (wipes volumes) or apply manually —
> see `docs/DYNAMIC_CONFIG.md` §1 for the mongosh one-liner.

## 3. Service ports (compose)

| Service | Port | Service | Port |
|---|---|---|---|
| api-gateway | 8080 | journey-service | 8092 |
| config-tenant-service | 8081 | reporting-service | 8093 |
| customer-identity-service | 8082 | ai-gateway | 8094 |
| admin-identity-service | 8083 | audit-service | 8095 |
| account-entitlement-service | 8084 | insurance-service | 8096 |
| dashboard-bff | 8085 | approval-service | 8097 |
| product-service | 8086 | | |
| usage-service | 8087 | | |
| billing-service | 8088 | | |
| payment-service | 8089 | | |
| notification-service | 8090 | | |
| content-service | 8091 | | |

All traffic from mobile/admin goes through **api-gateway :8080** only.

## 4. Seed data (what Dialog has on first boot)

| Area | Highlights |
|---|---|
| `tenant_configs` | `dialog-lk` — TELCO / OPERATOR, status ACTIVE, `providerBindings.default → com.selfcare.dialog.provider` |
| `theme_documents` | `dialog-default@1.0.0` — Dialog brand tokens |
| `navigation_documents` | `dialog-main` — home, balance, plans, bills/pay, shop/pack, otp, etc. |
| `layout_documents` | Prepaid + postpaid + hybrid, home + packages + bills + payments + usage + profile + support layouts (BalanceCard, UsageCard, QuickActionsGrid, …). Sections bind data via `dataSource` ids → `dataSources` manifest map |
| `component_catalog` | 36 components (telco + insurance + generic) — each maps to a Universal primitive + config recipe |
| `asset_documents` | Logo/icons/banners (CDN metadata) |
| `product_mapping_documents` | 15 packs (data/voice/combo/sms/roaming/IDD/DTV/loan/share-credit) |
| `feature_flags` | 28 flags |
| `client_integrations` | 8 integrations (see `docs/DYNAMIC_CONFIG.md` §2) |

### MySQL — seeded admin

| Field | Value |
|---|---|
| email | `admin@selfcare.io` |
| password | `Selfcare_Adm1n_P0rt4l_Pa55w0rd!2026` (bcrypt) |
| role | `SUPER_ADMIN` |

## 5. Runtime credentials & env

The `docker` profile sets (compose `x-selfcare-common`):

```yaml
X_DEFAULT_TENANT_ID: "dialog-lk"
SELFCARE_TENANT_DEFAULT_ID: "dialog-lk"
SPRING_PROFILES_ACTIVE: docker
```

- **Mongo URI** env var: `SPRING_MONGODB_URI=mongodb://selfcare:Selfcare_M0ng0_Db_Pa55w0rd!2026@mongodb:27017/selfcare_config?authSource=admin`
- **Redis**: `SPRING_DATA_REDIS_HOST=redis, PORT=6379, PASSWORD=Selfcare_R3d1s_Pa55w0rd!2026`
- **MySQL** per-service DB names + `SPRING_DATASOURCE_URL` (identity/audit/
  payment/… see `docs/INDEX.md` → runtime notes). Kafka bootstrap `kafka:9092`.

> Boot 4.1 uses `spring.mongodb.*` — the old `SPRING_DATA_MONGODB_URI` env is
> ignored. The compose file now sets the correct `SPRING_MONGODB_URI`.

## 6. AI for Dialog (no Anthropic key required)

- Route: **AI_PROVIDER** integration → `metadata.provider: "openai"` → Dialog
  chat traffic goes to the **OPENAI** provider.
- OpenAI integration seeded with `credentials.apiKey = "env:OPENAI_API_KEY"`
  and `defaultModel = "gpt-4o-mini"` (free tier).
- Set `OPENAI_API_KEY` in the environment before `docker compose up`; without a
  key the providers fail closed with a clear `… not configured for tenant` error
  (never a silent fallback to another key).
- Anthropic (`ANTHROPIC_API_KEY` / claude-sonnet-4-5) and Google
  (`GOOGLE_AI_API_KEY` / gemini-1.5-flash) are configured the same way and are
  usable by switching `AI_PROVIDER.metadata.provider` in Mongo.

### Smoke-test AI (no key needed for setup):

```bash
# Manifest is public (no auth):
curl -s -H "X-Tenant-Id: dialog-lk" http://localhost:8080/api/v1/config/manifest | head

# Chat (needs a key in env + a customer JWT):
curl -s -X POST http://localhost:8080/api/v1/ai/chat   \
  -H "X-Tenant-Id: dialog-lk" -H "Content-Type: application/json" \
  -H "Authorization: Bearer <jwt>" \
  -d '{"messages":[{"role":"user","content":"What is my balance?"}]}'
```

## 7. Mobile app

```bash
cd mobile/selfcare-app
# .env uses MOBILE_API_BASE_URL=http://localhost:8080 (the api-gateway)
npm install
npm run android   # or: npm run ios
```

The app fetches its compiled manifest from config-tenant-service through the
gateway, renders Dialog's server-driven layouts, and routes all API calls via
`MOBILE_API_BASE_URL`.

## 8. Flow: balance on the Dialog dashboard

```text
Mobile ExperienceScreen (manifest-driven, no hardcoded screens)
  → GET /api/v1/config/manifest            (api-gateway :8080, public, tenant dialog-lk)
  → LayoutRenderer renders sections (component + variant + dataSource from manifest)
  → SectionRenderer → dataSourceResolver.resolve("balance.current")
      → manifest.dataSources["balance.current"].service/endpoint (admin-authored)
      → ApiHandle.{bills,usage,catalog,content,notifications,payments,profile}[endpoint]
          → gateway /api/v1/... route → backend service → provider (from Mongo)
  → widgets fail independently (ADR-008) — one timeout never breaks the dashboard
```

The component catalog (`component_catalog` collection) maps each component id to
a Universal primitive + immutable config recipe. The app ships no
feature-specific screens — every screen is `ExperienceScreen` driven by the
compiled manifest (sections + dataSources + services). Adding a screen, a
section, or a data endpoint is a DB-level change, never an app release (v6 #1).

## 9. Troubleshooting quick hits

| Symptom | Check |
|---|---|
| All `/api/v1/config/**` → 401 | `MongoTenantValidator` bean missing → service has both web+webflux without `spring.main.web-application-type: servlet` |
| Old mongo env key staying | `SPRING_DATA_MONGODB_URI` is dead in Boot 4.1 — use `SPRING_MONGODB_URI` |
| Integration silently unresolvable | `client_integrations` row must use the FLAT schema (integrationType/baseUrl/…) — not legacy nested shape |
| AI says "API key not configured" | Set the `env:`-referenced key in the environment; `env:OPENAI_API_KEY` → `OPENAI_API_KEY` |
| Kafka consumer "No bean named kafkaListenerContainerFactory" | Boot 4.1 ships no Kafka auto-config — service must define `KafkaConfig` (see `docs/INDEX.md`) |