# ADR-010: Data Storage Decisions

## Status
Accepted — 2026-09-03

## Context
Different data types have different consistency, query, and scaling
requirements. We need a clear, per-data-type storage strategy.

## Decision

| Data | Store | Reasoning |
|------|-------|-----------|
| Configuration (theme, layout, journey, navigation, feature flags) | **MongoDB** | Document model fits nested config; flexible schema |
| Compiled runtime manifest | **In-process cache + Redis** | Read-heavy, immutable per version |
| Auth sessions | **MySQL InnoDB** + **Redis hot cache** | Durable, hot reads |
| Customer profile | **MySQL InnoDB** | Relational; Kafka-fed read model |
| Account/Connection | **MySQL InnoDB** | Relational; needs ACID |
| Compact entitlement (linked-connection lists) | **Redis AUTH** | Hot read; invalidated on Kafka change |
| Product/offer catalog | **MySQL InnoDB** | Relational; materialized views |
| Bills, payments | **MySQL InnoDB** | ACID; needs joins |
| Audit trail | **MySQL InnoDB** (append-only) | Durable, queryable |
| Chat sessions (AI) | **MongoDB** | Document model fits message history |
| RAG knowledge chunks | **Redis** + vector DB (prod) | Fast similarity search |
| Push tokens | **MySQL InnoDB** | Relational |
| Events | **Kafka** | Pub/sub; replay |
| Assets (logos, fonts) | **Object storage + CDN** | Large blobs |
| Logs | **ELK** | Search, not relational |
| Metrics | **Prometheus + Thanos** | Time-series |
| Traces | **Tempo / Jaeger** | Distributed tracing |

## Conventions

- **MySQL tables**: snake_case, all tables have `tenant_id` (NOT NULL), `created_at`, `updated_at`
- **MongoDB collections**: snake_case, all documents have `tenantId`, `_id` is UUID string
- **Redis keys**: `omobio:{tenantId}:{domain}:{key}`
- **Kafka topics**: `omobio.{domain}.{event-name}.v1`

## Migrations
- **Flyway** for MySQL (V{n}__{name}.sql)
- **MongoDB migration scripts** in `config-tenant-service/src/main/resources/seed/`

## Consequences

- Each data type gets the right tool
- Multi-tenancy is enforced at the storage layer (row/collection/key)
- Operational teams need to manage 4 stores (MySQL, MongoDB, Redis, Kafka) plus observability

## Alternatives considered

- **Single database (PostgreSQL)**: rejected — MongoDB is more natural for config
- **NoSQL-only**: rejected — relational data needs relational stores
- **Per-tenant databases**: rejected — operationally prohibitive
