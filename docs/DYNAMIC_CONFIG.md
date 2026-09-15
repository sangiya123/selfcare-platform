# Selfcare — Dynamic (DB-Backed) Configuration

Everything a tenant needs to run is resolved at runtime from MongoDB
(`tenant_configs` + `client_integrations`) and Redis cache — **nothing is
hardcoded** in backend or frontend source (v6/v7 §4). This document describes
the exact storage model, the credential resolution rules, and the AI provider
routing used by the current codebase (Spring Boot 4.1.1).

---

## 1. Collections — Mongo `selfcare_config`

The seed `deploy/local/mongo-init/01-tenant-dialog.js` creates/upserts:

| Collection | Purpose |
|---|---|
| `tenant_configs` | One row per tenant: id, name, industry, type, country, status, feature flags, `providerBindings` (default → provider package) |
| `client_integrations` | One row per tenant × integration: flat schema below |
| `theme_documents` | Brand: logo, colors/tokens, fonts |
| `navigation_documents` | Nav items / tabs / deep-link maps |
| `layout_documents` | Server-driven page layouts (widgets, order, availability) |
| `component_catalog` | Component → provider/data-source mapping |
| `asset_documents` | Asset registry (CDN keys, metadata) |
| `product_mapping_documents` | Tenant ↔ canonical product/offer mapping |
| `feature_flags` | Capability/menu toggles (28 seeded for Dialog) |

Seeds only apply to a **fresh** store on first container boot. To apply to an
existing compose stack manually:

```bash
docker compose cp deploy/local/mongo-init/01-tenant-dialog.js mongo-init:/01-tenant-dialog.js
docker compose exec mongo-init mongosh --quiet --authenticationDatabase admin \
  -u selfcare -p Selfcare_M0ng0_Db_Pa55w0rd!2026 < deploy/local/mongo-init/01-tenant-dialog.js
```

---

## 2. `client_integrations` — FLAT schema (authoritative)

> Do **not** use the old nested shape (`type` / `endpoint.baseUrl` /
> `capabilities`). The Java model `ClientIntegrationConfig` and the finder
> `findByTenantIdAndIntegrationType` expect the flat fields below. The old
> shape silently matches nothing → integrations unresolvable → 401/404.

| Field | Type | Meaning |
|---|---|---|
| `tenantId` | string | tenant key, e.g. `dialog-lk` |
| `industry` | string | `TELCO`, `INSURANCE`, … |
| `integrationType` | string | adapter key, see §4 |
| `providerClass` | string | FQCN of the industry-pack bean |
| `baseUrl` | string | upstream endpoint, e.g. `https://bss.dialog.lk/api/v3` |
| `authType` | string | `API_KEY`, `OAUTH2_CLIENT_CREDENTIALS`, `NONE` |
| `credentials` | map | secret-free references, see §3 |
| `fieldMapping` | map | optional legacy↔canonical field mapping |
| `advanced` | map | timeouts, retry policy, circuit breaker |
| `metadata` | map | AI routing + free-form flags |
| `status` | string | `ACTIVE` / `INACTIVE` |
| `health` | object | last health-check outcome |
| `createdAt` / `updatedAt` | date | audit timestamps |

### Credential keys used by providers

| Key | Used by |
|---|---|
| `clientId` / `clientSecret` | OAuth2 client-credentials providers (Dialog MIFE, AIA) |
| `apiKey` | BSS / SMSC / AI providers |
| `senderId` | SMSC sender name (e.g. `selfcare`) |
| `defaultModel` | AI providers only (§3) |

### Seeded Dialog integrations (8 in `01-tenant-dialog.js`)

| integrationType | baseUrl | authType | notes |
|---|---|---|---|
| `DIALOG_MIFE` | `https://auth.dialog.lk/oauth2` | OAUTH2_CLIENT_CREDENTIALS | auth/payment/recharge |
| `DIALOG_BSS` | `https://bss.dialog.lk/api/v3` | API_KEY | balance/usage/bill/connection |
| `DIALOG_SMSC` | `https://smsc.dialog.lk/api/v1` | API_KEY | notification |
| `DIALOG_CATALOG` | `https://catalog.dialog.lk/api/v2` | API_KEY | product catalog |
| `ANTHROPIC` | `https://api.anthropic.com/v1` | API_KEY | AI (see §3) |
| `OPENAI` | `https://api.openai.com/v1` | API_KEY | AI (see §3) |
| `GOOGLE_AI` | `https://generativelanguage.googleapis.com/v1beta` | API_KEY | AI (see §3) |
| `AI_PROVIDER` | — | — | metadata.provider routing (see §3) |

Dev-profile `ClientIntegrationSeeder` (config-tenant-service) seeds the same
tenants with **mock** endpoints (`*-mock.selfcare.io`) and placeholder keys —
only active on `spring.profiles.active=dev`, never in production.

---

## 3. AI provider configuration (v1.7/1.8 — DB-driven)

`ai-gateway` resolves every provider decision per tenant from the
`client_integrations` collection via `TenantConfigurationService`
(`getBaseUrl`, `getCredential`). No AI key or URL is hardcoded in Java.

### Providers and their integration types

| Java provider | integrationType | endpoint derived | model key |
|---|---|---|---|
| `AnthropicProvider` | `ANTHROPIC` | `baseUrl` + `/messages` | `defaultModel` (default claude-sonnet-4-5) |
| `OpenAiProvider` | `OPENAI` | `baseUrl` + `/chat/completions` | `defaultModel` (default gpt-4o-mini) |
| `GoogleAiProvider` | `GOOGLE_AI` | `baseUrl` + `/models/{model}:generateContent` | `defaultModel` (default gemini-1.5-flash) |
| `ContentModerationService` | `OPENAI` | `baseUrl` + `/v1/moderations` | property `selfcare.ai.moderation.base-url` fallback |
| `VectorEmbeddingService` | property-driven | `selfcare.ai.embedding.base-url` | property fallback (keyword mode when unset) |

### Credential resolution (`resolveApiKey` → `resolveCredential`)

Each provider's `resolveCredential(value)` supports exactly 3 forms:

| Form | Behaviour |
|---|---|
| `sk-...` / literal | returned as-is (only for throwaway/dev secrets) |
| `env:<VAR>` | read at runtime from `System.getenv("<VAR>")`; if unset/blank → treated as absent |
| `secret:<ref>` | returns `null` — resolution is delegated to the deployment layer (K8s Secret / Vault); provider skip |
| `${...}` | ignored — unresolved placeholder is skipped |

Seeded AI integrations therefore use `env:` references so free API keys live in
the environment, never in source:

```js
credentials: {
  apiKey: "env:OPENAI_API_KEY",          // set OPENAI_API_KEY in the shell/target env
  defaultModel: "gpt-4o-mini"
}
```

### Per-tenant AI routing (`LlmProviderRouter`)

- Router default provider: property `selfcare.ai.default-provider` (default
  `anthropic`).
- `switchProvider(tenantId)` reads the `AI_PROVIDER` integration and uses its
  `metadata.provider` to pick the effective LLM provider for that tenant.
- Example (seeded): Dialog routes to `openai` via `AI_PROVIDER.metadata =
  { provider: "openai" }`.

### Free-tier models (seeded defaults)

| Provider | defaultModel | API key env var |
|---|---|---|
| Anthropic | `claude-sonnet-4-5` | `ANTHROPIC_API_KEY` |
| OpenAI | `gpt-4o-mini` | `OPENAI_API_KEY` |
| Google AI | `gemini-1.5-flash` | `GOOGLE_AI_API_KEY` |

---

## 4. Integration type → provider class (routing)

`client_integrations.integrationType` is the adapter key. `providerClass`
points at the industry-pack implementation:

| integrationType | providerClass (Dialog) |
|---|---|
| `DIALOG_MIFE` | `com.selfcare.dialog.provider.DialogAuthProvider` |
| `DIALOG_BSS` | `com.selfcare.dialog.provider.DialogBalanceProvider` |
| `DIALOG_SMSC` | `com.selfcare.dialog.provider.DialogNotificationProvider` |
| `DIALOG_CATALOG` | `com.selfcare.dialog.provider.DialogProductCatalogProvider` |
| `AIA_INSURANCE` | `com.selfcare.aia.provider.AIAInsuranceProvider` |

JWT/identity JWKS uses `selfcare.security.jwt.base-url` (platform property,
default `http://localhost:8081`).

---

## 5. No-hardcoded audit rules (for future work)

- No `tenantId.equals("dialog-lk")` dispatch in core services — use the
  adapter registry, dispatch by `industry` in the tenant config.
- No API keys / operator base URLs in `*.java`, `*.ts`, `*.yml` outside
  dev-profile seeds and platform-level env defaults (OTel endpoint, localhost
  compose URLs).
- New provider? Extend `resolveCredential` patterns above; add the
  integrationType to the seed + `ClientIntegrationSeeder`.