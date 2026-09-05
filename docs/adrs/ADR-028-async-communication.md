# ADR-028: Asynchronous Communication — Kafka Events and CQRS

## Status
Accepted — 2026-09-04

## Context
The platform has:
- 20+ microservices that need to react to domain events
- Read models that are updated asynchronously from write models
- Audit trail that needs to capture all state changes
- Workflows that span multiple services

Synchronous HTTP between services causes tight coupling and cascade
failures. The spec requires:
- Event-driven read model updates
- Audit events for all state changes
- Saga pattern for cross-service transactions
- Event sourcing for change-governance

## Decision

### 1. Event backbone: Kafka
- All services publish domain events to Kafka
- Topic naming: `{tenant}.{service}.{entity}.{event}` (e.g. `dialog-lk.payment.transaction.completed`)
- Each service owns its producer; consumers are explicit
- Schema: Avro or Protobuf (JSON also accepted for v1)
- Retention: 7 days default, 30 days for audit events

### 2. Event schema (standard envelope)
```json
{
  "eventId": "01HV...",
  "eventType": "PAYMENT_COMPLETED",
  "aggregateType": "Payment",
  "aggregateId": "pay_123",
  "tenantId": "dialog-lk",
  "userId": "usr_456",
  "correlationId": "01HV...",
  "timestamp": "2024-09-04T10:00:00Z",
  "version": 1,
  "payload": { ... }
}
```

### 3. Consumer groups
- One consumer group per service
- Services own their read models
- Example:
  - `account-entitlement-service` subscribes to `*.payment.*`, `*.recharge.*`
  - Updates Redis compact entitlement cache on events
  - Replay from earliest offset on service restart

### 4. CQRS — read model updates
- **Write side**: authoritative service owns aggregate (e.g. `payment-service` for payments)
- **Read side**: consumer services maintain materialized views
- Example: dashboard-bff subscribes to balance/usage/billing events → builds cached dashboard payload

### 5. Saga pattern for cross-service transactions
Multi-step workflows (e.g. purchase → activate → notify) use a **saga**:
- `journey-service` is the saga orchestrator
- Each step publishes an event; next step triggered by previous completion
- Compensating transactions on failure (e.g. rollback activation if payment fails)
- Saga state stored in MongoDB (`saga_instances` collection)

### 6. Event sourcing for change governance
- `change-governance-service` stores every change as an immutable event
- Events: `ChangeDraftCreated`, `ChangeValidated`, `ChangeApproved`, `ChangePublished`
- Event log is the source of truth; current state is derived
- Enables full audit trail and time-travel debugging

### 7. Outbox pattern for reliable events
- Services write to an `outbox` table in the same DB transaction as the domain change
- `OutboxProcessor` (Kafka Connect or polling CDC) publishes to Kafka
- Guarantees at-least-once delivery; consumers must be idempotent
- Prevents "event lost if service crashes after DB commit"

### 8. Dead-letter queue (DLQ)
- Every Kafka consumer group has a DLQ topic: `{original-topic}.dlq`
- Failed messages (after 3 retries) go to DLQ
- Ops team monitors DLQ depth; alerts on non-empty DLQ

### 9. Schema registry
- Confluent Schema Registry for Avro/Protobuf schemas
- Registered schemas: `{service}-{entity}-{event-type}:v{version}`
- Compatibility: `BACKWARD` (new consumers read old messages)
- Schema evolution: add optional fields only (no remove/rename)

## Implementation
- `KafkaConfig` in `platform-common` (auto-configures producer/consumer)
- `DomainEvent` base class with envelope schema
- `OutboxProcessor` in `platform-common`
- `SagaOrchestrator` base class in `journey-service`
- `KafkaConsumerManager` in `platform-common` for graceful shutdown/rebalance handling

## Consequences

Positive:
- Decoupled services (no direct HTTP dependency)
- Natural audit trail from event log
- Scalable: Kafka handles backpressure and replay
- CQRS read models are eventually consistent and can be rebuilt

Negative:
- Eventually consistent (not suitable for synchronous reads without caching)
- Debugging distributed flows is harder
- Schema evolution requires discipline

## Compliance
- NFR-AUDIT: every state change emits an event
- NFR-AVL-001: no cascade failures via decoupled messaging
