# Runbook: AI Model Degradation

## Overview
This runbook covers scenarios where the AI model gateway experiences quality
degradation, provider outages, cost spikes, or safety policy violations.

## Symptoms
- High error rate from AI provider (AIProviderErrorRateHigh alert)
- AI cost spike > $100/hr (AICostSpike alert)
- Users report nonsensical AI responses
- Latency spikes from AI gateway
- AI governance kill switch activation needed

## Quick checks
1. **Is the ai-gateway service up?**
   ```bash
   kubectl -n omobio-prod get pods -l app=ai-gateway
   kubectl -n omobio-prod logs -l app=ai-gateway --tail=100 | grep -E "ERROR|provider|anthropic|openai"
   ```
2. **Is the AI provider responding?**
   ```bash
   curl -s https://api.anthropic.com/v1/models | jq '.data[].id' | head -5
   ```
3. **Check AI cost metrics**
   ```bash
   kubectl exec -it prometheus-0 -- promtool query instant \
     'increase(ai_cost_usd_total[1h])' | grep omobio
   ```
4. **Check kill switches**
   ```bash
   curl -s -H "X-Tenant-Id: platform" \
     http://api-gateway/api/v1/admin/ai-governance/kill-switches
   ```

## Common causes

### A. AI provider rate limit or outage
**Symptom**: High 429 responses, provider timeout errors
**Fix**: Activate provider-level kill switch to switch to fallback
```bash
# Via API
curl -X POST http://api-gateway/api/v1/admin/ai-governance/kill-switches \
  -H "X-Tenant-Id: platform" \
  -H "Content-Type: application/json" \
  -d '{
    "scope": "PROVIDER",
    "provider": "anthropic",
    "reason": "Rate limit exceeded",
    "activatedBy": "oncall-sre",
    "expiresAt": null
  }'
```

### B. Cost spike from runaway token usage
**Symptom**: AICostSpike alert, unusually high token counts
**Fix**: Lower the daily budget for affected use cases
```bash
# Check today's spend
curl -s http://redis:6379 \
  "HGETALL ai:token:usage:daily:$(date +%Y-%m-%d):<tenant-id>"

# Via API — temporarily lower budget
curl -X PUT http://api-gateway/api/v1/admin/ai-governance/use-cases/<use-case-id> \
  -H "X-Tenant-Id: platform" \
  -H "Content-Type: application/json" \
  -d '{"dailyBudgetUsd": 10.0}'
```

### C. Prompt injection attack causing unexpected behavior
**Symptom**: Model refuses legitimate requests or generates off-topic responses
**Fix**: Activate use-case kill switch; trigger evaluation run
```bash
curl -X POST http://api-gateway/api/v1/admin/ai-governance/kill-switches \
  -H "X-Tenant-Id: platform" \
  -H "Content-Type: application/json" \
  -d '{
    "scope": "USE_CASE",
    "useCaseId": "customer_chat",
    "reason": "Prompt injection detected",
    "activatedBy": "oncall-sre"
  }'
```

### D. Evaluation regression — model quality degraded
**Symptom**: ReleaseGateResult shows passed=false with regression flag
**Fix**: Roll back to previous model or disable use case pending re-eval
```bash
# Check release gate
curl -s http://api-gateway/api/v1/admin/ai-governance/release-gate/customer_chat

# If failed — activate kill switch and page AI team
```

### E. RAG context poisoning
**Symptom**: AI returns incorrect tenant-specific information
**Fix**: Flush RAG index and re-index from clean source
```bash
# Flush Redis cache for RAG
redis-cli KEYS "omobio:rag:*" | xargs redis-cli DEL

# Trigger re-index (via admin API)
curl -X POST http://config-service/api/v1/admin/rag/reindex \
  -H "X-Tenant-Id: platform" \
  -d '{"tenantIds": ["<affected-tenant>"]}'
```

## Fallback response
When the kill switch is active, `AIModelGateway.chat()` returns a deterministic
fallback message. The mobile app displays this without crashing.

## Post-incident
1. Review AI cost logs to identify root cause of spike
2. Run evaluation suite to confirm model quality is restored
3. Update RAG index with any missing context
4. Deactivate kill switch via `DELETE /api/v1/admin/ai-governance/kill-switches/{id}`

## Escalation
If the issue involves data residency violation or PII exposure via AI output,
page the Security team immediately. Do not deactivate the kill switch until
security has reviewed.
