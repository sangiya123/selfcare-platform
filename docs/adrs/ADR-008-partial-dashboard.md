# ADR-008: Partial Dashboard Response

**Status**: ACCEPTED
**Date**: 2026-09-03
**Deciders**: OMOBIO Architecture Council

## Context

The dashboard BFF aggregates widgets from multiple downstream services:
- Balance (usage-service)
- Usage (usage-service)
- Bills (billing-service)
- Bundles (product-service)
- Notifications (notification-service)
- AI recommendations (ai-gateway)
- Banners (content-service)
- Insurance policies (insurance-service, if industry = insurance)

If we wait for ALL widgets to complete before returning, the slowest one
determines the user-perceived latency. In production, even with circuit
breakers, a single slow downstream can stall the entire dashboard.

## Decision

**Partial response** is the contract:
- Each widget has its own timeout (default 300ms, configurable per widget)
- The overall dashboard has a deadline (default 500ms)
- When a widget fails or times out, the rest of the dashboard still returns
- Per-widget status is exposed in the response: SUCCESS, PARTIAL, STALE, TIMEOUT, UNAVAILABLE, ERROR

## Example response

```json
{
  "data": {
    "correlationId": "omobio-a1b2c3d4",
    "elapsedMs": 412,
    "overallStatus": "PARTIAL",
    "widgets": {
      "balance": {
        "widgetId": "balance",
        "status": "SUCCESS",
        "data": { "amount": 1250.00, "currency": "LKR" },
        "elapsedMs": 87
      },
      "banners": {
        "widgetId": "banners",
        "status": "TIMEOUT",
        "errorMessage": "Widget timed out after 300ms",
        "retryable": true
      },
      "bundles": {
        "widgetId": "bundles",
        "status": "ERROR",
        "errorMessage": "Provider 503",
        "retryable": true
      }
    }
  }
}
```

## Why this matters

- **User experience** — even with one slow service, the user sees 90% of the dashboard
- **Resilience** — one provider's outage doesn't black-hole the entire app
- **Cost** — fewer retries, lower tail latency

## Implementation

```java
Flux<WidgetResult> widgetFlux = Flux.fromIterable(executions)
    .flatMap(this::executeWidget)
    .takeUntilOther(Mono.delay(overallDeadline))
    .onErrorContinue((throwable, obj) -> {
        log.error("Widget error (continuing): {}", throwable.getMessage());
    });
```

Each widget executes concurrently via Project Reactor. The overall deadline
cancels in-flight widgets. Per-widget circuit breaker + timeout + bulkhead
applied via Resilience4j decorators.

## Widget statuses

| Status | Meaning | UI behavior |
|---|---|---|
| SUCCESS | Widget returned fresh data | Show data |
| PARTIAL | Widget returned partial data | Show with a small "stale" indicator |
| STALE | Cached data returned | Show with "updated X min ago" |
| TIMEOUT | Widget exceeded its timeout | Show "tap to retry" |
| UNAVAILABLE | Widget not configured for user/tenant | Hide |
| ERROR | Widget threw an exception | Show error state with retry |

## Refresh strategy

- `GET /api/v1/dashboard/home` returns the full dashboard
- `GET /api/v1/dashboard/widgets/{id}?connectionId=...` refreshes one widget
- `POST /api/v1/dashboard/home/refresh` with body `[widgetIds]` refreshes specific widgets

The client uses per-widget refresh to recover failed widgets without a full
dashboard reload.

## Configuration

```yaml
omobio:
  dashboard:
    overall-deadline-ms: 500      # hard deadline for the whole dashboard
    default-widget-timeout-ms: 300 # per-widget timeout
```

Per-widget overrides can be set in the layout document:

```yaml
sections:
  - id: balance
    component: BalanceCard
    states:
      timeout: 200   # override this widget's timeout
      unavailable: hide
      error: fallback
```

## Consequences

### Positive
- Predictable user experience under stress
- Operator outages don't black-hole the app
- Easier to add new widgets (failure is contained)

### Negative
- More complex client logic (per-widget state, retry, fallback)
- Some widgets may show stale data (mitigated by retryable flag)
