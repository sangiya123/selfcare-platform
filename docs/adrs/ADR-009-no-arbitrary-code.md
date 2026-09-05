# ADR-009: Config Cannot Execute Arbitrary Code

**Status**: ACCEPTED
**Date**: 2026-09-03
**Deciders**: OMOBIO Architecture Council, Security Team

## Context

Server-driven UI is a powerful pattern — non-developers (admins, product
managers) can change what users see without code deploys. But this power
must be bounded for security reasons.

If config documents could execute arbitrary code, a compromised admin
account or breached admin portal would have full code execution in user
sessions — a critical security incident.

## Decision

**Config can only reference registered, validated actions.** The set of
allowable actions is closed and audited.

## Allowed action types

```java
private static final Set<String> ALLOWED_ACTION_TYPES = Set.of(
    "NAVIGATE",          // Go to an in-app route
    "BACK",               // Pop navigation stack
    "START_JOURNEY",     // Begin a multi-step journey
    "CALL_API",          // Call a registered API
    "OPEN_WEB",          // Open in-app WebView
    "OPEN_WEB_SSO",      // Open in-app WebView with SSO
    "EXTERNAL_BROWSER",  // Open system browser
    "DEEP_LINK",         // Custom URL scheme
    "CALL_PHONE",        // Native dialer
    "EMAIL",             // Open mail composer
    "COPY",              // Copy to clipboard
    "SHARE",             // Native share sheet
    "DOWNLOAD",          // File download
    "MODAL",             // Show in-app modal
    "BOTTOM_SHEET",      // Show bottom sheet
    "LOGIN",             // Trigger auth flow
    "LOGOUT",            // End session
    "PAYMENT",           // Initiate payment
    "REFRESH"            // Refresh current view
);
```

Any action with a `type` not in this list fails config compilation.

## Allowed components

Every widget component ID must be registered in `ComponentRegistry` before
it can be used in a published layout. New components are added by:
1. Implementing the React Native component
2. Registering it in `ComponentRegistry.register(componentId, component, metadata)`
3. Submitting a new mobile app version that ships the new component

Until step 3 is complete, the new component is unusable in production.

## URL allowlist

`OPEN_WEB`, `EXTERNAL_BROWSER`, `DEEP_LINK` actions can only target URLs
in the tenant's URL allowlist. This prevents an admin from pointing users
to phishing sites.

```java
public boolean isUrlAllowed(String tenantId, String url) {
    TenantConfig config = tenantConfigService.get(tenantId);
    return config.getAllowedDomains().stream()
        .anyMatch(domain -> url.startsWith("https://" + domain));
}
```

## Component props validation

Each component declares a JSON Schema for its props. Layout documents are
validated against the component's schema at publish time.

```json
{
  "component": "BalanceCard",
  "props": {
    "showCurrency": true,
    "primaryColor": "#FF6B00",
    "maxLines": 2
  }
}
```

If the props don't match the schema, publish fails.

## API allowlist for CALL_API

`CALL_API` actions can only target registered backend routes. The route
registry is built at server startup from controller mappings.

## What we deliberately do NOT allow

- `eval()` / `Function()` / arbitrary JavaScript
- Native module bridges from config
- Custom URL schemes that aren't pre-registered
- File system access from config
- Database queries from config
- Network requests to non-allowlisted domains

## Auditing

Every config publish is recorded in the audit log:
- Who published (admin user ID)
- What changed (diff between versions)
- When (timestamp)
- The compiled manifest (immutable record)

## Consequences

### Positive
- Compromised admin = XSS-class issue, not code execution
- Security review only needed for new component registrations
- Predictable behavior across tenants
- Auditable

### Negative
- New widget requires a new app version
- Admins can't add arbitrary integrations
- Limits some advanced use cases (e.g. custom analytics events per widget)

## For truly custom behavior

If a tenant needs behavior outside the closed set, they must:
1. Build a new component (developer work)
2. Submit it via the standard app release process
3. Wait for the next mobile app rollout
