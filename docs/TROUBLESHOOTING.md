# OMOBIO Selfcare Platform — Troubleshooting Guide

This guide covers the most common issues and how to resolve them.

## 1. Tenant context issues

### Symptom
```
ERROR: No tenant context found. TenantContextFilter must run first.
```

### Cause
A service is being called without going through the api-gateway, or the
`X-Tenant-Id` header is missing.

### Fix
1. Ensure the request goes through the api-gateway
2. Verify the `X-Tenant-Id` header is present
3. If calling from a test, set the context manually:
```java
TenantContext ctx = new TenantContext();
ctx.setTenantId("dialog-lk");
TenantContext.set(ctx);
```

## 2. Config not updating

### Symptom
Customer sees old layout, branding, or features even after publishing new config.

### Cause
Cached manifest in service, Redis, or mobile app.

### Fix
1. **Service cache**: The L1 cache TTL is 5 minutes. Wait, or restart.
2. **Redis cache**: Check `omobio:config:{tenantId}:*` keys.
   ```bash
   redis-cli DEL omobio:config:dialog-lk
   ```
3. **Mobile cache**: The app uses ETag-based conditional GET.
   Force refresh:
   ```typescript
   sdk.config.forceRefresh();
   ```

## 3. AI chat returns "fallback" responses

### Symptom
All AI chat responses are template-based; real LLM responses never come.

### Cause
No API key configured, or key is invalid.

### Fix
1. Set environment variable:
   ```bash
   export ANTHROPIC_API_KEY=sk-ant-api03-...
   ```
2. Restart the AI gateway
3. Check logs: `kubectl logs -f deployment/ai-gateway | grep "API key"`

## 4. Mobile app can't reach backend

### Symptom
Network errors, "Failed to fetch" in mobile console.

### Cause
Wrong base URL, cleartext blocked, or wrong tenant header.

### Fix
1. **Android emulator**: Use `http://10.0.2.2:8080` (not `localhost`)
2. **iOS simulator**: Use `http://localhost:8080` or LAN IP
3. **Real device**: Use LAN IP, e.g., `http://192.168.1.10:8080`
4. Verify cleartext is allowed (debug only): `android:usesCleartextTraffic="true"`
5. Check the network_security_config.xml allows your dev domain

## 5. AI chat rate limited

### Symptom
429 Too Many Requests when using the AI assistant.

### Cause
Tenant or user rate limit exceeded.

### Default limits
- Per tenant: 60 RPM, 60k TPM
- Per user: 20 RPM

### Fix
1. **Wait** — limits reset every minute
2. **Admin**: Adjust limits in `omobio.ai.rate-limit.*` config
3. **Admin**: Use a different tenant for testing

## 6. Step-up auth not working

### Symptom
Payment for high-value transactions fails with "step-up required".

### Cause
The cross-connection or high-value rule is triggering step-up auth,
but the customer is not completing it.

### Fix
1. Verify the customer has access to the OTP channel (SMS, email, WhatsApp)
2. Check the notification service is reachable
3. Verify the step-up token TTL (default 5 min)
4. Review `payment-service` logs for OTP send errors

## 7. Dashboard partial responses

### Symptom
Some widgets show "Unable to load" or "Timeout".

### Cause
This is by design (ADR-008). Per-widget timeout + circuit breaker.

### Fix
1. Check which widget timed out in the response
2. Verify the upstream service is healthy
3. Check the widget timeout (default 2s in manifest, 5s overall)
4. Customer can pull-to-refresh to retry the failed widget

## 8. Cross-tenant data leakage (CRITICAL)

### Symptom
A user from tenant A sees tenant B's data.

### Immediate action
1. **Page the on-call engineer**
2. Disable cross-tenant access in the api-gateway:
   ```bash
   curl -X PATCH https://api.omobio.com/admin/tenants/{id}/lockdown
   ```
3. Investigate: which service, which query, which user
4. Run conformance test: `mvn test -Dtest=TenantIsolationTest`

### Root causes to check
- Missing `tenantId` filter in a query
- Hardcoded tenant ID in code
- Cached result from a different tenant
- Misconfigured `TenantContext` propagation

## 9. Mobile app crashes on launch

### Symptom
App shows splash then crashes, or never reaches home screen.

### Cause
- Manifest fetch failure
- Theme not applied
- Component not registered

### Fix
1. Check the app log (`adb logcat | grep ReactNativeJS`)
2. Verify the tenant has a published manifest
3. Verify the SDK initialized: `sdk.initialized === true`
4. Check network connectivity to the API

## 10. Token expired errors

### Symptom
Customer is logged out unexpectedly.

### Cause
Access token expired (default 42 days, per ADR-011).

### Fix
1. Customer must sign in again
2. Verify refresh token is still valid
3. Check session creation in `customer-session-service` logs

## 11. AI tool execution fails

### Symptom
AI responds with "I tried to do X but couldn't".

### Cause
The downstream service the tool calls is down, or the tool is not permitted.

### Fix
1. Check tool permissions in `omobio:ai:tools:{tenantId}:{userId}`
2. Verify the target service is healthy (e.g., `usage-service`, `product-service`)
3. Check the audit log for the failed tool call
4. APPROVAL-requiring tools will return "PENDING_APPROVAL" — that's expected

## 12. iOS build fails

### Symptom
```
error: Pods not installed
```

### Fix
```bash
cd ios
rm -rf Pods Podfile.lock
pod install
```

## 13. Android build fails (Gradle)

### Symptom
```
Could not resolve all dependencies
```

### Fix
```bash
cd android
./gradlew clean
./gradlew --refresh-dependencies assembleDebug
```

## 14. Mobile app shows "Configuration not available"

### Symptom
The home screen says "Dashboard loading…" indefinitely.

### Cause
The manifest endpoint is failing or the response is malformed.

### Fix
1. Test the endpoint manually:
   ```bash
   curl -H "X-Tenant-Id: dialog-lk" http://localhost:8080/config/manifest
   ```
2. Check the config-tenant-service logs
3. Verify the tenant exists in MongoDB
4. Verify the manifest is published (not just drafted)

## 15. AI recommendations are stale

### Symptom
Recommendations show the same plans for every customer.

### Cause
Either the recommendation service isn't running, or the features are stale.

### Fix
1. Force refresh in the app (pull-to-refresh)
2. Clear MMKV cache: `clearStorage()` in dev
3. Check the AI gateway logs for recommendation service errors
4. Verify usage data is being fed in (last 30 days)
