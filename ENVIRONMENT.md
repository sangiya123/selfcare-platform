# Environment Configuration Guide

## Two configuration layers

| Layer | Where it lives | What it contains |
|-------|----------------|------------------|
| **Platform** | `.env.<env>` files (committed as templates) | DB, Redis, Kafka, JWT secret, AI model, observability, feature-flag provider, policy thresholds |
| **Client / Tenant** | MongoDB collections, configured via selfcare Studio admin | Theme, layout, journeys, feature flags, **client API URLs + credentials**, AI assistants |

**Client-specific config does NOT live in env files.** It lives in the database
and is managed via the admin portal. See [docs/MULTI_TENANT_CONFIG.md](docs/MULTI_TENANT_CONFIG.md)
for the full architecture.

---

## Single-line environment switch

Set **`SELFCARE_ENV`** to one of: `dev`, `stg`, `reg`, `prod`. That's the only knob.

The full stack — backend (Java/Spring), admin (Vite/React), mobile (React Native) — all read this variable and load the matching `.env.<env>` file.

---

## Files

| File | Purpose |
|------|---------|
| `.env.example`     | Template — committed to git, lists every PLATFORM-LEVEL variable |
| `.env.dev`         | Local docker-compose stack |
| `.env.stg`         | Staging cluster |
| `.env.reg`         | Regional pre-production |
| `.env.prod`        | Production template (real secrets from secret manager) |
| `.env`             | Symlink or copy of the active env (gitignored) |
| `env-loader.sh`    | Bash loader — sources the active env into the current shell |
| `env.sh`           | Universal launcher: `./env.sh stg make backend-build` |
| `Makefile`         | One-line commands: `make dev`, `make stg`, `make reg`, `make prod` |

---

## How it works

### Backend (Spring Boot)

Spring auto-loads `application-{profile}.yml` based on `SPRING_PROFILES_ACTIVE` (= `SELFCARE_ENV`).

All env defaults are in `application.yml` (the base config); per-env overrides live in `application-{dev,stg,reg,prod}.yml`. Every value is a `${ENV_VAR:default}` reference — no hardcoded URLs.

### Admin (Vite / React)

Vite loads `.env.<mode>` automatically based on `--mode` flag (set by `SELFCARE_ENV` via Makefile).

`src/lib/config.ts` is the central place that reads Vite env vars. `src/lib/api.ts` is the central HTTP client that uses `config.apiGatewayUrl` for all backend calls.

### Mobile (React Native)

`scripts/load-env.sh <env>` copies `.env.<env>` to `.env`. The SDK reads `process.env.MOBILE_*` at runtime.

---

## Quick start

```bash
# Set environment (once per shell)
export SELFCARE_ENV=dev

# Backend
make backend-run    # reads $SELFCARE_ENV, runs all services with matching profile

# Admin
make admin-run      # vite --mode=$SELFCARE_ENV

# Mobile
make mobile-run     # loads .env.$SELFCARE_ENV, starts Metro

# Switch environments — only change SELFCARE_ENV
export SELFCARE_ENV=stg
make backend-run admin-run mobile-run
```

---

## What belongs in env files vs. DB

| Put in `.env.<env>` | Put in MongoDB (configure via admin) |
|---------------------|--------------------------------------|
| DB/Redis/Kafka/Mongo hosts + credentials | Operator API base URLs |
| JWT signing secret | Operator API auth credentials |
| AI model + Anthropic key (platform-wide) | Theme tokens (colors, typography) |
| OTEL/Prometheus/Grafana endpoints | Page layouts |
| Unleash URL | User journeys |
| Policy thresholds (max payment, etc.) | Feature flags (per tenant) |
| Cache TTLs | Notification templates |
| Reconciliation interval | AI assistant personas + tool permissions |
| Public API base URL | Per-tenant content (articles, FAQs, banners) |

**Rule of thumb:** if it changes when you add a new operator, it goes in the DB. If it changes when you change deployment target (dev → stg → prod), it goes in env.

---

## Adding a new env value

1. Add the key (with a placeholder) to **every** `.env.*` file
2. Reference it in code as `${VAR_NAME}` (Spring) or `import.meta.env.VITE_VAR_NAME` (Vite)
3. If it's a secret, mark `${SECRET_*}` so the secret manager pipeline picks it up
4. Commit and deploy — never commit the real values

---

## Secret management

- **dev**:   real values in `.env.dev` (not committed, dev-only)
- **stg/reg/prod**: `${SECRET_*}` placeholders are substituted at deploy time by:
  - Kubernetes: `secrets.env` ConfigMap + sealed-secrets / external-secrets-operator
  - Docker: `--env-file secrets.env` (never committed)
  - CI: GitHub Actions / Jenkins pulls from Vault / AWS SM

`env-loader.sh` resolves `${SECRET_*}` from the environment if it's set, else leaves the placeholder (and the app fails fast with a clear error).

---

## See also

- [docs/MULTI_TENANT_CONFIG.md](docs/MULTI_TENANT_CONFIG.md) — how operator config is managed in DB
- [docs/OPERATOR_ONBOARDING.md](docs/OPERATOR_ONBOARDING.md) — adding a new operator
- [docs/API_REFERENCE.md](docs/API_REFERENCE.md) — REST API reference

