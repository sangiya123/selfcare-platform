# Runbook: Tenant Isolation Leak (CRITICAL)

## CRITICAL
A tenant isolation leak is a **SEV-1 security incident**. Treat as urgent.
Page the security team immediately.

## Symptoms
- Customer A sees Customer B's data
- Tenant A's admin sees Tenant B's records
- Logs show data from one tenant in another's request flow
- Cross-tenant error responses

## Immediate actions

1. **Confirm the leak**
   - Check audit logs for the affected sessions
   - Check the TenantContext values in service logs
   - Check the X-Tenant-Id header in API gateway access logs

2. **Halt the affected service**
   ```bash
   kubectl -n omobio-prod scale deploy/<service> --replicas=0
   ```

3. **Capture evidence**
   - Snapshot affected database rows
   - Save service logs
   - Note request IDs, correlation IDs, user IDs

4. **Notify**
   - Page security team
   - Notify the affected tenants' account managers
   - File a security incident ticket

## Common causes

### A. Missing X-Tenant-Id in request
**Symptom**: Logs show `tenantId=<null>` in services
**Fix**: The api-gateway is stripping the header — check `TenantRoutingGatewayFilterFactory`

### B. SQL query missing tenant predicate
**Symptom**: Query returns rows from multiple tenants
**Fix**: Add `WHERE tenant_id = ?` to all queries; add a test that runs as each tenant

### C. Redis cache key collision
**Symptom**: Cached value for tenant A returned to tenant B
**Fix**: Verify all Redis keys include tenantId in the key

### D. Provider adapter used wrong tenant
**Symptom**: Service called provider with wrong tenantId
**Fix**: Verify the provider receives tenantId from the request, not from a static value

## Recovery
1. Identify the root cause
2. Fix and add a regression test
3. Re-enable the service
4. Run the conformance suite
5. Review all recent access logs for the affected tenants

## Post-incident
- File a post-mortem
- Update the conformance suite
- Add a permanent regression test
- Consider adding tenant-isolation checks to the load balancer
