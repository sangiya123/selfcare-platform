# ADR-021: Observability Stack — OpenTelemetry + Prometheus + ELK

## Status
Accepted — 2026-09-04

## Context
The platform requires end-to-end observability across:
- Gateway (Spring Cloud Gateway)
- BFF (Dashboard BFF)
- 19+ microservices (Java reactive stack)
- Provider integrations (HTTP/SOAP/SQL)
- Database (MySQL, MongoDB, Redis)
- Event bus (Kafka)
- Object storage

The spec requires:
- OpenTelemetry-compatible tracing, metrics, logs
- Correlation/trace ID propagated to every call
- RED metrics for services
- USE metrics for infrastructure
- Business KPIs separated from technical telemetry
- SLO/error-budget dashboards per operator
- AI calls record model, latency, tokens, cost, policy decisions

## Decision
We adopt a **unified observability stack**:

### 1. Tracing
- OpenTelemetry SDK in all Java services
- OTLP exporter to `otel-collector` sidecar
- Distributed trace context propagated via W3C `traceparent` header
- Correlation ID added to MDC for log correlation
- Span attributes include: tenantId, userId (hashed), env, service, capability, endpoint, provider, journey, configVersion, appVersion

### 2. Metrics
- Micrometer → Prometheus
- Required dimensions: tenant, environment, service, capability, endpoint, provider, journey, configVersion, appVersion
- RED metrics per service: Rate, Errors, Duration
- USE metrics per host: Utilization, Saturation, Errors
- Business KPIs: separate `selfcare_business_*` metric namespace
- AI: `selfcare_ai_call_total{model=X,use_case=Y,outcome=Z}`
- AI tokens: `selfcare_ai_tokens_total{model=X,use_case=Y,type=input|output}`

### 3. Logs
- Structured JSON logs to stdout
- Logback with `LogstashEncoder`
- Fields: timestamp, level, service, tenantId, correlationId, traceId, spanId, message, attributes
- PII masked at logger level (ADR-018)
- Shipped to ELK via Filebeat
- Retention: 30 days default, configurable per environment

### 4. Dashboards
- 12 Grafana dashboards:
  - Platform overview
  - Per-service: customer-identity, payment, ai-gateway, content, insurance, mobile-performance
  - Operational: dashboard-bff, auth-redis-health, kafka-consumer-lag, db-connection-pools
  - Config: config-publish
  - Security: tenant-isolation
- 10 Prometheus alert groups (platform-availability, dashboard-bff, identity-security, ai-gateway, capacity, billing, payment, notification, insurance, kafka, auth-redis)

### 5. Required dimensions (per spec)
- operator, environment, service, capability, endpoint
- provider, journey, configVersion, appVersion/platform
- LOB/customerType (non-PII)
- transaction type/status

### 6. Do NOT log
- raw access/refresh tokens
- card data
- OTP codes
- full profile payload
- sensitive identifiers unless masked and approved

### 7. SLO targets (per operator)
- Customer-facing APIs: 99.95% monthly availability
- P95 latency < 300ms (server processing)
- P95 latency < 100ms (cached/local)
- Error rate < 0.5%
- Dashboard P95 < 600ms (initial partial)

## Implementation
- `OpenTelemetryConfig` in `platform-common` (auto-configures tracing)
- `MetricsConfig` (auto-configures Micrometer + Prometheus)
- `LoggingFilter` (auto-adds correlationId to MDC)
- Sidecar `otel-collector` in every service deployment

## Consequences

Positive:
- Single observability stack (no fragmentation)
- OpenTelemetry avoids vendor lock-in
- Correlation across all telemetry types
- Per-tenant SLO tracking

Negative:
- OTel collector sidecar adds resource overhead
- ELK log volume can be high
- Cardinality of high-cardinality labels must be bounded

## Compliance
- Spec § "Observability"
- NFR-OBSERVABILITY
- Runbook-driven SRE process
