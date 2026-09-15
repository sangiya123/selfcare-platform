# Incident Response Runbook

This is the master runbook for the Selfcare Platform.

For specific runbooks (widget timeout, admin auth outage, etc.), see
[observability/runbooks/](../observability/runbooks/).

## Severity classification

| Severity | Definition | Initial response | Resolution target |
|---|---|---|---|
| **SEV-1** | Platform down, all tenants affected | < 5 min | < 1 hour |
| **SEV-2** | Major feature broken, multiple tenants | < 30 min | < 4 hours |
| **SEV-3** | Single tenant / non-critical issue | < 4 hours | < 1 business day |
| **SEV-4** | Informational / improvement | next business day | next sprint |

## Incident commander

Every SEV-1 and SEV-2 has a designated incident commander. The IC:
1. Coordinates all responders
2. Owns the decision to escalate, communicate, and resolve
3. Documents the timeline
4. Initiates the post-mortem after resolution

## Communication

| Channel | Audience | When |
|---|---|---|
| Slack `#platform-incidents` | Engineering | All incidents |
| Slack `#platform-status` | Internal stakeholders | SEV-1, SEV-2 |
| Email `status@selfcare.io` | Tenant admins | SEV-1, SEV-2 |
| Phone tree | Customer executives | SEV-1 only |

### Status updates

Update the status page at least every 30 minutes during SEV-1/SEV-2:
- https://status.selfcare.io

## First-responder checklist (SEV-1)

1. **Acknowledge the alert** (PagerDuty)
2. **Open Slack incident channel** (`#inc-YYYY-MM-DD-<short-name>`)
3. **Declare yourself IC** (or assume)
4. **Page the secondary on-call** if needed
5. **Check the dashboards** — Grafana link in PagerDuty
6. **Form a hypothesis** — which service, which tenants, which metric
7. **Mitigate first, diagnose second**:
   - Roll back recent deploys
   - Roll back recent config changes
   - Scale out (HPA) or restart
   - Failover to backup region
8. **Communicate** — first status update within 15 minutes
9. **Resolve** — verify metrics return to normal
10. **Document** — start the timeline immediately

## Common SEV-1 patterns

### All requests failing (502/503/504 spike)
- **Likely cause**: API gateway or auth service down
- **Mitigation**: Scale out, check database connectivity
- **See**: Service-specific runbooks

### One tenant completely down
- **Likely cause**: Tenant-specific config issue or provider outage
- **Mitigation**: Disable failing widgets, communicate to tenant
- **See**: [insurer-outage.md](../observability/runbooks/insurer-outage.md)

### Latency spike
- **Likely cause**: Database, downstream, or network
- **Mitigation**: HPA, circuit breaker engagement
- **See**: [widget-timeout.md](../observability/runbooks/widget-timeout.md)

### Security incident
- **Likely cause**: Tenant isolation leak, breach, etc.
- **Mitigation**: Halt affected service, page security
- **See**: [tenant-isolation-leak.md](../observability/runbooks/tenant-isolation-leak.md) (CRITICAL)

## Post-incident

Every SEV-1 and SEV-2 gets a post-mortem within 5 business days.

Format: blameless, timeline-based, with action items.

See [templates/postmortem.md](../templates/postmortem.md).
