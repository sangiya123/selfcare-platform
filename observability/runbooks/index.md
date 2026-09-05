# On-Call Runbooks

This directory contains incident response runbooks for the platform.

## Runbook index

| Runbook | When to use |
|---|---|
| [widget-timeout.md](widget-timeout.md) | Dashboard widgets timing out or erroring |
| [dashboard-bff-widget-timeouts.md](dashboard-bff-widget-timeouts.md) | Dashboard BFF circuit breaker open |
| [admin-auth-outage.md](admin-auth-outage.md) | Admin Studio login/auth issues |
| [insurer-outage.md](insurer-outage.md) | Insurer API down (AIA, etc.) |
| [config-publish-failure.md](config-publish-failure.md) | Config Service won't publish a manifest |
| [tenant-isolation-leak.md](tenant-isolation-leak.md) | Cross-tenant data leak suspected |
| [db-connection-pool-exhaustion.md](db-connection-pool-exhaustion.md) | Hikari pool exhaustion / MySQL too many connections |
| [kafka-consumer-lag.md](kafka-consumer-lag.md) | Kafka consumer lag growing, stale downstream data |
| [ai-model-degradation.md](ai-model-degradation.md) | AI gateway errors, cost spikes, kill switch |
| [kafka-lag.md](kafka-lag.md) | Kafka consumer lag investigation |
| [payment-timeout.md](payment-timeout.md) | Stuck PENDING payments, provider timeout, reconciliation |

## Severity levels

| Severity | Definition | Response time |
|---|---|---|
| SEV-1 | Platform down, all tenants affected | < 5 minutes |
| SEV-2 | Major feature broken, multiple tenants | < 30 minutes |
| SEV-3 | Single tenant / non-critical issue | < 4 hours |
| SEV-4 | Informational / improvement | next business day |

## Escalation

| Level | Who | Contact |
|---|---|---|
| L1 | On-call engineer | PagerDuty |
| L2 | Platform lead | Slack `#platform-incidents` |
| L3 | Architecture Council | Phone tree |

## Useful commands

```bash
# Check service health
kubectl -n omobio-prod get pods -l app=admin-identity-service
kubectl -n omobio-prod logs -f deploy/admin-identity-service --tail=100

# Check recent errors
kubectl -n omobio-prod logs -l app=admin-identity-service --since=10m | grep -i error

# Check metrics
curl http://admin-identity-service:8082/actuator/metrics/http.server.requests

# Port-forward to debug
kubectl -n omobio-prod port-forward svc/admin-identity-service 8082:8082

# Run a one-off job
kubectl -n omobio-prod run debug --rm -it --image=alpine -- sh
```

## Dashboards

| Service | Grafana dashboard |
|---|---|
| All services | [Platform Overview](../../observability/grafana/dashboards/platform-overview.json) |
| Dashboard BFF | [Dashboard BFF](../../observability/grafana/dashboards/dashboard-bff.json) |
| Customer identity | [Customer Identity](../../observability/grafana/dashboards/customer-identity.json) |
| Payment | [Payment](../../observability/grafana/dashboards/payment.json) |
| AI gateway | [AI Gateway](../../observability/grafana/dashboards/ai-gateway.json) |
