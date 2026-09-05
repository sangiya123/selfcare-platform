# Runbook — <alert or service name>

| Field | Value |
|---|---|
| **Runbook ID** | RB-NNNN |
| **Service** | e.g. `payment-service`, `api-gateway`, `customer-identity-service` |
| **Alert name** | Grafana / alertmanager alert name this runbook covers |
| **Severity** | P1 (down) / P2 (degraded) / P3 (warning) |
| **Owner** | name + email + PagerDuty schedule |
| **Last updated** | YYYY-MM-DD |
| **Tested** | YYYY-MM-DD (date of last tabletop exercise) |

## Alert Definition

What is the alert and what does it mean?

> Example: "Payment service error rate > 5% over 5 minutes."

**PromQL / source query:**

```promql
sum(rate(http_requests_total{service="payment-service",status=~"5.."}[5m]))
  / sum(rate(http_requests_total{service="payment-service"}[5m])) > 0.05
```

**Threshold rationale:** …

**False positive likelihood:** low / medium / high

## Impact

What is the user-facing or business impact when this alert fires?

- Customer-facing: …
- Revenue: …
- Compliance: …
- Other services: …

## Triage (first 5 minutes)

1. **Acknowledge the alert** in PagerDuty.
2. **Open dashboards:** list of Grafana dashboards to look at
3. **Check recent changes:** was there a recent deploy? Config change?
4. **Check upstream / downstream:** is the problem ours or another team's?
5. **Determine scope:** all users, single tenant, single region, single LOB?

## Investigation

For each component suspected, list the diagnostic commands / queries.

### Logs (Kibana / Loki)

```
service:payment-service AND level:ERROR AND timestamp:>now-15m
```

### Metrics (Prometheus / Grafana)

- `rate(http_requests_total[5m])` — request volume
- `histogram_quantile(0.99, ...)` — p99 latency
- `payment_provider_error_rate` — per-provider
- `db_connection_pool_active` — DB saturation

### Traces (Jaeger / OTEL)

- Filter by service=`payment-service` and tag `error=true`
- Inspect downstream spans: `payment-provider-http`, `db-query`,
  `idempotency-store`

### Recent Events

- `kubectl rollout history deployment/payment-service -n omobio-prod`
- `kubectl get events -n omobio-prod --sort-by=.lastTimestamp | head -20`
- `argocd app history payment-service-prod`

## Mitigation

For each known cause, list the remediation step.

### Cause 1: Recent deploy introduced a regression

1. **Check rollout status:** `kubectl rollout status deployment/payment-service -n omobio-prod`
2. **Roll back:** `kubectl rollout undo deployment/payment-service -n omobio-prod`
3. **Verify:** error rate returns to baseline within 2 minutes
4. **Page on-call engineer** for the team that shipped the deploy

### Cause 2: Downstream payment provider is down

1. **Check provider status page:** …
2. **Open circuit breaker** (skip failing provider, route to alternate)
3. **Notify provider support** with correlation IDs
4. **Page customer support** if outage > 15 minutes

### Cause 3: Database connection pool exhausted

1. **Check current connections:** `SELECT count(*) FROM pg_stat_activity;`
2. **Identify long-running queries:** `SELECT pid, query, state FROM pg_stat_activity WHERE state='active' ORDER BY xact_start;`
3. **Kill long queries if appropriate:** `SELECT pg_terminate_backend(<pid>);`
4. **Scale connection pool:** increase `spring.datasource.hikari.maximum-pool-size`
5. **Page DBA on-call**

## Verification

After mitigation, verify the alert has cleared and user impact is
resolved:

- [ ] Error rate back to < 0.5% (well below alert threshold)
- [ ] p99 latency back to < 1.5s
- [ ] No customer complaints in last 15 minutes (Zendesk / Sentry)
- [ ] Synthetic probes passing
- [ ] Grafana dashboard "back to normal" annotation added

## Postmortem

If the alert was a P1 or P2, schedule a postmortem within 48 hours.
Use the `templates/postmortem.md` template.

**Required attendees:** on-call, service owner, SRE, security (if
data-related)

**Blameless tone:** focus on systems, not people.

## Related

- **Dashboards:** …
- **Logs / queries:** …
- **Traces / services:** …
- **Other runbooks:** …
- **ADRs:** …
