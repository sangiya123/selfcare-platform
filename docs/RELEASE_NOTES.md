# OMOBIO Selfcare Platform — Release Notes

## v1.0.0 (2026-09-03) — Initial Production Release

### Highlights
First production-ready release of the OMOBIO Selfcare Platform.
Supports multi-industry, multi-tenant selfcare experiences for
Dialog, Hutch, Airtel (telco) and AIA (insurance) — all from a
single codebase, configured by data.

### Backend (17 services)
- ✅ api-gateway — single entry point, tenant routing, rate limiting
- ✅ platform-common — tenant context, adapter registry, security, observability
- ✅ config-tenant-service — theme/layout/feature-flag resolution, Mongo + Redis
- ✅ customer-identity-service — OTP auth, JWT, JWKS
- ✅ admin-identity-service — SAML + TOTP for admin
- ✅ account-entitlement-service — Dialog entitlement rule (ADR-006)
- ✅ dashboard-bff — resilient widget orchestrator, partial response (ADR-008)
- ✅ product-service — product catalog
- ✅ usage-service — balance/usage/allowance
- ✅ billing-service — bills, documents
- ✅ payment-service — payments, idempotency, step-up auth
- ✅ notification-service — push/SMS/email with adapter pattern
- ✅ content-service — CMS, articles, FAQs
- ✅ journey-service — multi-step journey runtime
- ✅ reporting-service — async report generation
- ✅ ai-gateway — full AI platform (see AI section below)
- ✅ audit-service — append-only audit trail
- ✅ insurance-service — AIA insurance BFF

### Frontend
- **Selfcare Studio** (admin): 14 pages, 6-tab AI Studio, page builder, journey builder, integration builder
- **Selfcare App** (mobile): 8 screens, full SDK, server-driven UI, AI assistant, MMKV caching

### AI Platform (15 endpoints, 30+ features)
- **LLM Providers**: Anthropic, OpenAI, Google AI (stub), Fallback
- **Streaming**: SSE-based, chunk-by-chunk delivery
- **Tools**: 7 built-in tools (balance, usage, plans, recommend, support, bill, recharge)
- **RAG**: keyword + vector embeddings + similarity search
- **Sessions**: persistent, MongoDB, rolling window
- **Intent classification**: 10 intents, rule-based
- **Sentiment analysis**: 5 levels, negation, triggers
- **Conversation summarization**: topics + intent + sentiment + resolution
- **Search**: semantic, cosine similarity, tenant-scoped
- **Moderation**: OpenAI Moderation API + 10 jailbreak patterns
- **Rate limiting**: per-tenant + per-user (RPM + TPM)
- **Token tracking**: cost estimation (Claude + OpenAI pricing)
- **Prompts**: 6 industry templates + per-tenant overrides
- **Fallback**: always-on no-LLM mode
- **Multi-tenant**: provider, API key, prompts, KB, usage per tenant

### Key Architectural Decisions
- ADR-001: One product, no client forks
- ADR-002: React Native + TypeScript
- ADR-003: Java 25 + Spring reactive
- ADR-004: MongoDB config, immutable runtime manifest
- ADR-005: Config compiler pipeline (new in 1.0.0)
- ADR-006: Dialog linked-connection entitlement
- ADR-007: Tenant isolation (defense in depth)
- ADR-008: Partial dashboard response
- ADR-009: No arbitrary code in config
- ADR-010: Data storage decisions
- ADR-011: Token policy (42-day access / 7-month refresh)
- ADR-012: Multi-industry abstraction

### Testing
- **Backend**: 20 unit test files, ~150 test cases
- **Admin**: 8 Vitest test files
- **Mobile**: 11 Jest test files
- **Conformance**: 20 TestNG files
- **E2E**: 5 Detox specs

### Observability
- 12 Grafana dashboards
- 7 runbooks
- OpenTelemetry traces (Jaeger + Tempo)
- Prometheus metrics
- ELK logging
- Prometheus alert rules

### Deployment
- 17 Dockerfiles
- Helm chart with 8 templates
- 19 per-service/per-client values files
- GitOps: ArgoCD ApplicationSet, AppProject, Kustomize
- Jenkins + GitHub Actions pipelines

### Documentation
- 12 ADRs
- AI Features reference
- Developer Setup Guide
- Release Notes (this file)
- Mobile SDK reference
- API reference

### Multi-industry support
- **Telco**: Dialog, Hutch, Airtel (Sri Lanka)
- **Insurance**: AIA (10 countries: LK, BD, NP, PK, SG, MY, TH, VN, ID, PH)
- Industry packs: telco (connection-centric), insurance (policy-centric)
- Industry-specific terminology enforced

### Known limitations
- ADR-011: Token policy requires security review before production sign-off
- Mobile Detox E2E not yet running in CI
- Google AI provider is a stub (no real Gemini integration)
- No voice input yet (planned for v1.1)
- No image/multimodal input yet (planned for v1.1)

### Upgrade notes
This is the first release. No upgrade path required.
