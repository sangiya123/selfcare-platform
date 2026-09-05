# Runbook: Dashboard Widget Timeout

## Symptoms
- Users see "tap to retry" on dashboard widgets
- Widget status = TIMEOUT in dashboard response
- p95 latency for dashboard-bff > 500ms (the overall deadline)

## Quick checks

1. **Which widget(s) are timing out?**
   - Look at dashboard-bff logs for "Widget timeout" entries
   - Check the `widget` label in the response

2. **Which downstream service is slow?**
   ```promql
   histogram_quantile(0.95,
     sum(rate(http_client_requests_seconds_bucket{
       job=~".*-service",status!~"5.."
     }[5m])) by (le, uri, peer)
   ```

3. **Is the circuit breaker open?**
   ```bash
   curl http://dashboard-bff:8085/actuator/health/circuitBreakers
   ```

## Common causes

### A. Downstream service is genuinely slow
**Symptom**: One specific widget consistently > 300ms
**Fix**: Increase widget timeout temporarily, or scale downstream

```yaml
# In layout document or service config
sections:
  - id: balance
    states:
      timeout: 500   # override default 300ms
```

### B. Network partition
**Symptom**: All widgets time out simultaneously
**Fix**: Check network connectivity, peering, DNS

### C. Downstream pool exhaustion
**Symptom**: Cascading timeouts, increasing with traffic
**Fix**: Scale out downstream pods (HPA should trigger)

### D. Bad config change
**Symptom**: Started after a config publish
**Fix**: Roll back the config in admin UI
   - Admin → Configurations → Layouts → [tenant] → Versions → Rollback

## Recovery
1. The dashboard returns PARTIAL response (per ADR-008) — other widgets still work
2. Users see "tap to retry" — client refreshes the specific widget
3. Once downstream recovers, the next refresh succeeds

## Escalation
If > 50% of widgets are timing out for > 10 minutes, page the platform on-call.
