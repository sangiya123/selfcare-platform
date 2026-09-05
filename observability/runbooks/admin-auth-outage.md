# Runbook: Admin Authentication Outage

## Overview
This runbook covers scenarios where admin authentication (login, refresh, MFA)
fails or is degraded.

## Symptoms
- Admins cannot log in
- 401 responses on /api/v1/auth/login
- High latency on /api/v1/auth/**
- MFA verification failing
- Token refresh failing

## Quick checks
1. **Is the admin-identity-service up?**
   ```bash
   kubectl -n omobio-prod get pods -l app=admin-identity-service
   kubectl -n omobio-prod logs -l app=admin-identity-service --tail=100
   ```
2. **Is MySQL reachable?**
   ```bash
   kubectl -n omobio-prod exec -it admin-identity-service-0 -- nc -zv mysql 3306
   ```
3. **Is Redis reachable?**
   ```bash
   kubectl -n omobio-prod exec -it admin-identity-service-0 -- nc -zv redis 6379
   ```

## Common causes

### A. Database connection pool exhausted
**Symptom**: 500 errors, HikariPool exhaustion logs
**Fix**: Increase pool size or restart service
```bash
kubectl -n omobio-prod set env deploy/admin-identity-service SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=50
kubectl -n omobio-prod rollout restart deploy/admin-identity-service
```

### B. JWT signing key missing
**Symptom**: Startup failure, "No signing key configured" error
**Fix**: Verify secrets are mounted
```bash
kubectl -n omobio-prod get secret admin-identity-secrets -o yaml
```

### C. SAML IdP unreachable
**Symptom**: SSO callback fails, 502 errors
**Fix**: Verify IdP is up; check tenant config for correct SAML metadata URL

### D. MFA time skew
**Symptom**: TOTP codes rejected
**Fix**: Verify NTP synchronization
```bash
kubectl -n omobio-prod exec -it admin-identity-service-0 -- ntpdate -q pool.ntp.org
```

## Escalation
If the issue persists > 30 minutes, page the platform on-call.
