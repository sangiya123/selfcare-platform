# ADR-003: Java 25 + Spring Reactive for Backend

**Status**: ACCEPTED
**Date**: 2026-09-03
**Deciders**: OMOBIO Architecture Council

## Context

The backend powers a multi-tenant, multi-industry selfcare platform with:
- 16 microservices
- Real-time requirements (chat, push, balance refresh)
- Strict tenant isolation
- High availability (99.95% target)
- Heavy use of streaming/event-driven patterns (Kafka)

We must choose the language and framework for the new product.

## Decision

**Java 25** (LTS) + **Spring Boot 3.4** + **Spring Cloud** + **Project Reactor**.

## Rationale

### Why Java 25
1. **LTS** — Java 25 is the latest LTS; supported through 2031+
2. **Records** — concise immutable data carriers (used heavily in our DTOs)
3. **Pattern matching** — cleaner tenant-context switches
4. **Virtual threads** (Project Loom) — perfect fit for our blocking JDBC + I/O workloads
5. **Mature ecosystem** — Spring, Hibernate, Resilience4j, jjwt, OpenTelemetry all first-class

### Why Spring Boot 3.4
- Native compatibility with Java 25
- Spring WebFlux for reactive BFFs (dashboard-bff, ai-gateway)
- Spring MVC for traditional services (most domain services)
- Spring Cloud Gateway for API gateway
- Spring Data JPA / MongoDB / Redis — uniform repository abstraction

### Why Reactor
- BFFs need streaming composition (Widget fan-out with deadlines)
- AI gateway streams LLM responses
- Backpressure handling for high-load scenarios

## Architecture

### Module structure
```
backend/
├── platform-common/      # Shared: tenant, adapter registry, security, web, observability
├── api-gateway/          # Spring Cloud Gateway (WebFlux)
├── config-tenant-service/  # Mongo + Redis (reactive Mongo)
├── customer-identity-service/  # MySQL JPA + Redis
├── account-entitlement-service/  # MySQL JPA + Redis + Kafka
├── dashboard-bff/        # WebFlux + Resilience4j
├── product-service/      # JPA + Mongo + Redis + Kafka
├── usage-service/        # JPA + Redis
├── billing-service/      # JPA + Redis
├── payment-service/      # JPA + Redis + Kafka
├── notification-service/  # Mongo + Redis + Kafka
├── content-service/      # Mongo + Redis
├── journey-service/      # Mongo + Redis
├── reporting-service/    # JPA + async
├── ai-gateway/           # WebFlux (LLM streaming)
├── audit-service/        # JPA append-only
├── admin-identity-service/  # JPA + Redis
└── insurance-service/    # WebFlux BFF for insurance
```

### Virtual threads for blocking workloads

For services that are I/O heavy but not reactive (most of them), we use
virtual threads via `spring.threads.virtual.enabled=true`. This gives us
the simplicity of blocking code with the scalability of reactive.

### Common patterns

**Provider / Adapter pattern** — every external integration implements
`ApiAdapter`. The `ApiAdapterRegistry<T>` dispatches by tenantId.

**Configuration over env** — client-specific config (URLs, credentials) is
fetched from MongoDB `client_integrations` collection at runtime, not from
environment variables. Admin UI manages these.

**Tenant context propagation** — `TenantContext` ThreadLocal + `TenantResolverFilter`
extracts tenant from `X-Tenant-Id` header.

## Consequences

### Positive
- Mature, well-known stack — easy hiring
- Predictable performance with virtual threads
- Strong typing + Spring DI reduces boilerplate
- Excellent observability (OTel, Micrometer)
- 16 services share `platform-common` (DRY)

### Negative
- Cold start slower than Go/Rust (mitigated by GraalVM native for hot services)
- Higher memory usage (mitigated by per-tenant pod isolation)
- More verbose than Kotlin (but we chose Java for the talent pool)

## Performance targets

- API Gateway: p95 < 30ms overhead per request
- Dashboard BFF: p95 < 500ms (overall deadline)
- Customer identity: p95 < 200ms for OTP verify
- All services: p99 < 1s for read endpoints
