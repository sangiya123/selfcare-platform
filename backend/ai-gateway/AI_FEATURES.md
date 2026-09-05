# OMOBIO AI Platform — Complete Feature Set

The OMOBIO AI platform (`ai-gateway` service) provides a comprehensive, multi-tenant
AI capability for selfcare. The platform covers the full AI feature set requested.

## LLM Provider Support

| Provider | Status | Models | Streaming | Tools |
|----------|--------|--------|-----------|-------|
| **Anthropic** | ✅ Production | claude-sonnet-4-5, claude-opus-4-5, claude-haiku-4-7 | SSE | ✅ |
| **OpenAI** | ✅ Production | gpt-4o-mini, gpt-4o, gpt-4-turbo | SSE | ✅ |
| **Google AI** | ✅ Stub | gemini-1.5-flash, gemini-1.5-pro, gemini-1.0-pro | — | — |
| **Fallback** | ✅ Always-on | Template-based, no API | — | — |

Provider routing: per-tenant configurable via `omobio.ai.provider` config key.
Default: anthropic. Router: `LlmProviderRouter`.

## RAG (Retrieval-Augmented Generation)

| Feature | Status | Description |
|---------|--------|-------------|
| Keyword RAG | ✅ | `RAGService` — keyword + scoring from cached FAQ |
| Vector embeddings | ✅ | `VectorEmbeddingService` — OpenAI text-embedding-3 + keyword fallback |
| Semantic search | ✅ | Cosine similarity, Redis-backed store |
| KB admin UI | ✅ | Add, search, similarity test in AI Studio |
| Per-tenant chunks | ✅ | Chunks isolated by tenantId |

## Streaming

| Feature | Status | Description |
|---------|--------|-------------|
| Server-Sent Events | ✅ | `AIGatewayStreamingController` |
| Stream lifecycle | ✅ | create → chunk → done/error → close |
| Multi-instance | ⚠️ | Local sinks (Redis pub/sub for production) |
| Resumable | ❌ | Last-N-chunk replay only |

Endpoints:
- `POST /api/v1/ai/chat/stream` → returns `{streamId}`
- `GET  /api/v1/ai/chat/stream/{streamId}/events` → SSE
- `GET  /api/v1/ai/chat/stream/{streamId}` → stream metadata

## Tools (Function Calling)

7 built-in tools:

| Tool | Type | Description | Permission |
|------|------|-------------|------------|
| `get_balance` | Telco | Check current balance | AUTO |
| `get_usage` | Telco | Data/voice/SMS usage | AUTO |
| `list_plans` | Telco | Available plans | AUTO |
| `recommend_plan` | Telco | ML recommendation | AUTO |
| `create_support_ticket` | Both | Open support ticket | AUTO |
| `pay_bill` | Telco | Pay a bill | **APPROVAL** |
| `recharge` | Telco | Top up balance | **APPROVAL** |

Permission levels: `AUTO` (executes), `APPROVAL` (queued), `DENIED` (blocked), `NONE`.

## Recommendations & ML

| Feature | Status | Endpoint |
|---------|--------|----------|
| Bundle recommendations | ✅ | `GET /api/v1/ai/recommendations/{connId}` |
| Churn risk scoring | ✅ | `GET /api/v1/ai/churn/{connId}` |
| LTV-based offers | ⚠️ Stub | `RecommendationService` |
| Usage pattern detection | ⚠️ Stub | Same |

## Safety & Moderation

| Feature | Status | Description |
|---------|--------|-------------|
| OpenAI Moderation API | ✅ | `ContentModerationService` — hate, harassment, etc. |
| Prompt injection guard | ✅ | 10 regex patterns, blocks jailbreaks |
| Output filtering | ✅ | PII leak detection on outputs |
| Configurable per tenant | ✅ | Via admin AI Studio |

## Token & Cost Management

| Feature | Status | Description |
|---------|--------|-------------|
| Per-tenant RPM limit | ✅ | 60 req/min default |
| Per-tenant TPM limit | ✅ | 60k tokens/min default |
| Per-user RPM limit | ✅ | 20 req/min default |
| Rate limit tracking | ✅ | `TokenUsageService.recordRateLimitViolation` |
| Cost estimation | ✅ | Claude $3/$15, OpenAI $0.15/$0.60 per 1M tokens |
| Daily usage aggregation | ✅ | Per-tenant, per-user, per-model |
| Admin usage dashboard | ✅ | `GET /api/v1/admin/ai/usage` |

## Prompt Management

| Feature | Status | Description |
|---------|--------|-------------|
| Per-tenant overrides | ✅ | Redis-backed, 24h TTL |
| Industry templates | ✅ | telco, insurance, billing, support, sales, default |
| Variable substitution | ✅ | `{{tenantName}}`, `{{userName}}`, etc. |
| Admin editor | ✅ | AI Studio "Prompts" tab |
| Versioning | ❌ | Last-write-wins; consider git-style for production |

## Session Management

| Feature | Status | Description |
|---------|--------|-------------|
| Persistent sessions | ✅ | MongoDB, 7-day default retention |
| Rolling window | ✅ | Max 100 messages per session |
| Auto-title from first message | ✅ | First user message becomes title |
| Soft delete | ✅ | `active=false` |
| Cross-device sync | ✅ | Sessions are user-scoped, not device-scoped |

Endpoints (all under `/api/v1/ai/sessions`):
- `GET  /` — list
- `POST /` — get or create
- `GET  /{id}/history` — full message history
- `POST /{id}/messages` — append message
- `DELETE /{id}` — soft delete

## Sentiment Analysis

| Feature | Status | Description |
|---------|--------|-------------|
| Single-message | ✅ | `POST /api/v1/ai/sentiment` |
| Aggregate | ✅ | `POST /api/v1/ai/sentiment/aggregate` |
| 5-level scale | ✅ | VERY_NEGATIVE → VERY_POSITIVE |
| Negation handling | ✅ | "not good" → negative |
| Confidence score | ✅ | Matches / 5 |
| Triggers list | ✅ | Matched words for explainability |

## Conversation Summarization

| Feature | Status | Description |
|---------|--------|-------------|
| Structured summary | ✅ | Headline, intent, topics, sentiment, resolution, follow-ups |
| Topic extraction | ✅ | TF-IDF-style frequency |
| Resolution status | ✅ | RESOLVED / UNRESOLVED / PENDING |
| Suggested follow-ups | ✅ | 3 rule-based suggestions |
| Action items | ✅ | Tool calls listed |

Endpoint: `POST /api/v1/ai/summarize`

## Search

| Feature | Status | Description |
|---------|--------|-------------|
| Semantic search | ✅ | `GET /api/v1/ai/search?query=...&topK=5` |
| Cosine similarity ranking | ✅ | |
| Per-tenant scoped | ✅ | Chunks isolated by tenant |
| Latency tracking | ✅ | Search response includes ms |

## Fallback (No-LLM Mode)

Always available, kicks in when:
1. LLM provider returns error
2. Circuit breaker opens (5+ failures)
3. Direct request to `/api/v1/ai/chat/fallback`

Strategy:
1. Classify intent (local rule-based)
2. Try direct tool execution (e.g., get_balance)
3. Try RAG keyword search
4. Return templated response

## Observability

| Feature | Status | Description |
|---------|--------|-------------|
| Resilience4j CB | ✅ | `ai.gateway.chat` circuit breaker |
| Resilience4j retry | ✅ | 2 attempts, 500ms wait |
| Resilience4j timeout | ✅ | 30s per request |
| OpenTelemetry tracing | ✅ | Configured in `application.yml` |
| Prometheus metrics | ✅ | `actuator/prometheus` endpoint |
| Grafana dashboard | ✅ | `ai-gateway.json` |

## Multi-Tenant Isolation

| Feature | Status | Description |
|---------|--------|-------------|
| Per-tenant provider | ✅ | LLM provider switchable per tenant |
| Per-tenant API key | ✅ | Resolved at runtime |
| Per-tenant prompt | ✅ | Overrides base templates |
| Per-tenant rate limit | ✅ | RPM/TPM tracking |
| Per-tenant usage | ✅ | Token counts by tenant |
| Per-tenant knowledge | ✅ | Chunks scoped to tenant |

## Industry Awareness

| Term | Telco Pack | Insurance Pack |
|------|-----------|----------------|
| Account | Connection | Policy |
| Balance | Balance | Coverage |
| Top up | Recharge | Premium payment |
| Data | Data (GB) | Coverage (sum insured) |
| Voice | Voice (min) | Hotline |
| Plan | Plan / Package | Policy / Plan |
| Customer | Subscriber | Policyholder |
| Identifier | MSISDN | Policy number |

The AI uses these terms correctly per the active industry pack.

## Mobile SDK

`AIClient` (TypeScript):
- `chat()` — standard chat
- `startStream()` + `consumeStream()` — streaming
- `classifyIntent()`
- `getBundleRecommendations()` + `getChurnScore()`
- `analyzeSentiment()` + `summarize()` + `search()`
- `fallbackChat()`
- `listSessions()` / `getOrCreateSession()` / `deleteSession()`
- `health()`

Hooks:
- `useAIChat` — full chat experience with streaming
- `useRecommendations` — bundle recs + churn with 1h MMKV cache

UI:
- `AIChatScreen` — streaming chat
- `AISuggestionsCard` — home screen widget
- `ChatBubble` — message renderer with streaming cursor

## Admin UI (AI Studio)

6 tabs:
1. **Overview** — provider, requests, tokens, cost, feature flags
2. **Model** — provider/model selection, API key
3. **Tools** — AUTO/APPROVAL/DENIED per tool
4. **Prompts** — system prompt editor with variable preview
5. **Knowledge** — index chunks, similarity search
6. **Safety** — moderation + injection guardrails

## Tests

| Layer | Test Files | Coverage |
|-------|-----------|----------|
| Backend services | 4 new (Sentiment, Summarization, Fallback, existing AiGatewayService) | Intent, RAG, fallback flow |
| Mobile hook | `useAIChat.test.ts` | Initial state, prompts, urgent intent |
| Mobile client | `AIClient.test.ts` | All methods, SSE parsing, local cache |
| Admin page | `AIStudioPage.test.tsx` | Tabs, provider, request count |

## Pending / Future Work

- **Voice input**: speech-to-text integration (Google STT / Whisper)
- **Multi-language**: prompt templates in other languages
- **A/B testing**: per-prompt-variant traffic split
- **Image input**: vision support in OpenAI/Anthropic
- **Real-time transcription**: for live call support
- **Tool marketplace**: user-defined custom tools
- **Fine-tuning**: per-tenant model fine-tuning
- **Redis pub/sub** for cross-instance streaming
- **Vector DB integration** (Pinecone / Weaviate) for production
- **A2A protocol** (Agent-to-Agent) for multi-agent workflows
- **MCP** (Model Context Protocol) integration
