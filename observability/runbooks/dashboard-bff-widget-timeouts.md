# Runbook: Dashboard BFF Widget Timeouts

**Severity**: Warning
**Service**: dashboard-bff
**Alert**: `WidgetTimeoutRateHigh` (> 10% timeout rate for 5 minutes)

## Overview

The Dashboard BFF orchestrates all configured widgets in parallel. When a downstream
service is slow or down, widgets time out individually. The overall dashboard returns
a partial response.

## Initial Triage

1. **Check alert details** — which widget IDs are timing out?
2. **Check Grafana dashboard** — `OMOBIO Dashboard BFF — Resilience`
3. **Identify downstream** — each widget calls a specific downstream service
4. **Check downstream health** — query the downstream service's health endpoint

```bash
# Example: check product-service health if ProductCard widget is timing out
curl -s -H "X-Tenant-Id: dialog-lk" https://product-service/health/ready
```

## Investigation Steps

### 1. Identify the failing widget

```promql
# Top widgets by timeout count
topk(10,
  sum by (widget_id) (increase(widget_status_total{status="TIMEOUT"}[10m]))
)
```

### 2. Check downstream latency

```promql
# P99 latency for the downstream service
histogram_quantile(0.99,
  sum by (le) (rate(http_server_requests_seconds_bucket{job="product-service"}[5m]))
)
```

### 3. Check circuit breaker state

```promql
# Open circuit breakers
circuitbreaker_state{state="open"}
```

### 4. Check tenant distribution

```promql
# Are timeouts concentrated on one tenant?
sum by (tenant_id) (rate(widget_status_total{status="TIMEOUT"}[5m]))
```

## Common Causes

### 1. Downstream service slow
**Symptom**: All tenants affected, latency high
**Fix**: Check downstream service health, scale up, or increase widget timeout

### 2. Downstream service down
**Symptom**: Timeouts go to 100% for affected widgets
**Fix**: Check downstream health, circuit breaker should auto-open, alert downstream team

### 3. Network issue
**Symptom**: Multiple services affected
**Fix**: Check service mesh / network policies

### 4. Tenant-specific issue
**Symptom**: One tenant affected
**Fix**: Check tenant's downstream config, API keys, rate limits

## Mitigation

### Short-term
- Increase widget timeout in config (transient only)
- Disable failing widget via feature flag
- Manual rollback of config if caused by recent publish

### Long-term
- Tune downstream service capacity
- Improve circuit breaker thresholds
- Add fallback data sources for critical widgets

## Recovery Verification

```bash
# Check timeout rate has returned to normal
curl -s "http://prometheus/api/v1/query?query=rate(widget_status_total%7Bstatus%3D%22TIMEOUT%22%7D%5B5m%5D)"
```

Should be < 5% within 10 minutes of resolution.

## Escalation

If timeout rate remains > 10% for 30+ minutes:
- Page on-call SRE
- Notify Product team
- Prepare customer communication
