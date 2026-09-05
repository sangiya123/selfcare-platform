# Runbook: Insurer Outage (e.g. AIA API down)

## Overview
This runbook covers scenarios where an insurer provider (AIA, etc.) API is
unreachable, causing insurance widget timeouts on the dashboard.

## Symptoms
- Insurance widgets showing TIMEOUT/ERROR in dashboard
- High error rate on /api/v1/insurance/policies/**
- p95 latency spike for insurance-service

## Quick checks
1. **Verify AIA status page** (https://status.aia.com)
2. **Check insurance-service circuit breaker state**
   ```bash
   curl http://insurance-service:8096/actuator/health
   ```
3. **Check recent error rate**
   ```promql
   sum(rate(http_server_requests_seconds_count{uri="/api/v1/insurance/policies", status=~"5.."}[5m]))
   ```

## Common causes

### A. AIA API outage
**Symptom**: Provider returns 5xx
**Fix**: No action needed — dashboard returns PARTIAL response per ADR-008.
Inform users via banner. Insurance widgets show "Tap to retry".

### B. AIA rate limit
**Symptom**: 429 responses
**Fix**: Reduce widget refresh frequency. Check for runaway clients.
```yaml
# Adjust in insurance-service-values.yaml
widget:
  refresh-cooldown: 30s
```

### C. Auth credentials rotated
**Symptom**: 401 responses from AIA
**Fix**: Update client_integrations collection in MongoDB
```javascript
db.client_integrations.updateOne(
  { tenantId: "aia-lk", providerKey: "insurance" },
  { $set: { "config.clientId": "new-id", "config.clientSecret": "new-secret" } }
)
```
Then invalidate Redis cache:
```bash
redis-cli DEL "omobio:aia-lk:integration:insurance"
```

### D. Cross-region latency spike
**Symptom**: Timeouts but not 5xx
**Fix**: Increase widget timeout or enable stale-while-revalidate.

## Mock mode
For non-production tenants, mock mode can be enabled as a stopgap:
```yaml
omobio:
  aia:
    mock-mode: true
```

## Escalation
If the outage is > 1 hour, page the insurer relationship manager.
