# ADR-013: AI Governance, Risk Tiers, and Release Gate

## Status

Accepted — 2026-09-03.

## Context

The AI scope spec (`Planning doc/06_ai/02_AI_Governance_Evaluation.md`) and
the security spec (`Planning doc/07_security/01_Security_Privacy_Compliance.md`)
require enforceable controls around AI use cases. Without a registry, every
new feature risks bypassing the controls; without a release gate, regressions
ship silently; without a kill switch, an incident cannot be stopped quickly.

This ADR documents the controls the platform provides and the rules the
AI services must follow when calling LLMs.

## Decision

### 1. Use case registry (`ai_use_cases` table)

Every AI-backed capability is registered as an `AiUseCase` row with:
- `risk_tier` ∈ {`LOW`, `MEDIUM`, `HIGH`} (per spec)
- `approved_providers` — comma-separated, validated against the platform
  allow list (`anthropic`, `openai`, `google`)
- `default_model`
- `allowed_tools` — comma-separated tool names (per spec "tool allow list")
- `human_approval_required` — gate before output is acted on (per spec)
- `retention_days` — per spec retention policy (1..365)
- `daily_budget_usd` — per spec cost/token budget
- `max_tokens_per_call`
- `user_daily_token_limit`
- `data_residency` ∈ {`IN`, `US`, `EU`, `ANY`} — per spec operator/data-residency
- `pii_mask_input` / `pii_mask_output` — per spec PII masking
- `enabled` — used by the kill switch too

### 2. Risk tiers (per spec)

- **LOW:** content draft, FAQ search, dev explanation.
  - Default: human approval not required, retention 30 days.
- **MEDIUM:** recommendation, ranking, support summary, admin config draft.
  - Default: human approval not required, retention 30 days.
- **HIGH:** payment/financial action prep, profile/security changes,
  claim decision support.
  - Default: human approval REQUIRED, retention ≤ 90 days,
  budget enforcement stricter, must log to audit.

### 3. Prompt / template versioning (`ai_prompt_versions`)

Each prompt template has a monotonic version. Production uses the row
where `is_active = true`. Versions are immutable — change creates a new
row. `content_hash` (SHA-256) supports change detection in CI.

### 4. Tool allow list and authorization

`allowed_tools` on each use case restricts which tools the LLM may call.
Authorization is layered on top via `ToolPermissionService`:
- Tools call normal authorized services (per spec: "AI tools call normal
  authorized services; model output never grants access")
- Per the security spec, AI tools go through the same RBAC/ABAC as
  non-AI APIs.

### 5. Knowledge-source provenance

For RAG, every retrieved chunk carries source metadata (operator, document
id, last-updated, allowed roles). RAG queries are filtered by tenant and
user authorization BEFORE content reaches the model (per spec: "RAG
isolation — each operator has separate document/index/vector namespaces
and access policies").

### 6. PII classification and masking (`PiiMaskingService`)

Recognizes: email, phone, MSISDN, national ID, passport, payment card,
policy number, account number, IP, JWT, secret keys.

Per-tenant rules can extend defaults. Default strategies:
- Most PII: `PARTIAL` (last 4 visible)
- JWTs and secrets: `REDACT`

Logging pipeline calls `PiiMaskingService.mask(...)` for any text field
that may contain PII. Per the NFR: "PII masking in logs/telemetry;
production debug payload logging disabled by default."

### 7. Evaluation and release gate (`AiEvaluationService`, `ai_evaluation_results`)

Per spec: "Every AI use case ships with a versioned evaluation set,
baseline score, regression threshold and red-team cases. A model/provider
change is treated like a software release and may be rolled back
independently."

Tracked dimensions: task success, factual accuracy, hallucination rate,
tool-selection accuracy, policy compliance, prompt-injection resistance,
refusal correctness, latency, cost, multilingual quality.

Pass criteria (defaults, configurable per use case):
- task success ≥ 0.70
- hallucination ≤ 0.10
- injection resistance ≥ 0.85
- policy compliance ≥ 0.95

Regression: if a new run's task success drops > 5pp from the previous
run, the result is flagged `[REGRESSION]` and `passed = false`.

Release gate endpoint: `GET /api/v1/admin/ai-governance/release-gate/{useCaseId}`.
Pipeline should block deployment of a model change if the gate fails.

### 8. Kill switch (`ai_kill_switches`)

Two scopes:
- `USE_CASE` — disable a specific use case for a tenant (or all tenants
  if `tenant_id` is null)
- `PROVIDER` — disable a specific provider globally

Active = true and `expires_at > now()` ⇒ blocked.
Per the security spec: "model/provider allow list and emergency disable
switch."

Activation is auditable via the audit service (record `RATE_LIMIT` /
`CONFIG` events with `action = KILL_SWITCH_ACTIVATED`).

### 9. Cost and token budget

`TokenUsageService` tracks usage in Redis with daily keys. The
governance service consults `tokenUsageService.todaySpend(tenantId)`
before each call. If `todaySpend > useCase.dailyBudgetUsd`, the call
is rejected with a deterministic error and a fallback response is
returned.

### 10. Human approval workflow

For `risk_tier = HIGH` use cases, `human_approval_required = true` is
set on the use case. The platform's tool-calling flow shows a
deterministic summary of the proposed action and requires explicit
user confirmation before the action is executed (per spec).

## Consequences

- AI use cases are first-class entities in the platform, not loose code paths.
- Admins can add/remove/modify use cases without code change.
- Model/provider changes are treated as releases (rollout, rollback,
  evaluation) — no silent regression in production.
- Kill switch can be activated in seconds, before an incident spreads.
- PII cannot leak through logs (masking is mandatory for AI services).
- Slightly more upfront work to register a use case, but the same
  service call (single line) is needed at runtime — `enforcePolicy(...)`.

## Alternatives considered

- **Implicit use-case policy** (config-file only). Rejected: not auditable,
  not enforceable at runtime, no kill switch, no per-tenant override.
- **Service-mesh-level LLM policy** (Istio + Envoy ext-authz).
  Rejected: adds infra complexity and a second source of truth;
  registry in the gateway is simpler and tested.
- **Per-call ad-hoc checks.** Rejected: easy to forget; we centralize
  the policy decision in `AiGovernanceService.enforcePolicy(...)`.

## References

- `Planning doc/06_ai/02_AI_Governance_Evaluation.md`
- `Planning doc/07_security/01_Security_Privacy_Compliance.md`
- `Planning doc/02_requirements/02_Non_Functional_Requirements.md` (PII masking)
- ADR-009 (no arbitrary code in config) — also relevant: the prompt
  template registry applies the same principle
- `backend/ai-gateway/src/main/java/com/selfcare/ai/service/AiGovernanceService.java`
- `backend/ai-gateway/src/main/java/com/selfcare/ai/service/PiiMaskingService.java`
- `backend/ai-gateway/src/main/java/com/selfcare/ai/service/AiEvaluationService.java`
