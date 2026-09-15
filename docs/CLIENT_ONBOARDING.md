# Client Onboarding Guide

This guide walks through bringing a new client (Dialog, Hutch, AIA, ...)
onto the Selfcare Platform.

## Overview

Onboarding has 5 phases:
1. **Discovery** — requirements, integrations, timelines
2. **Tenant setup** — config, credentials, theme
3. **Provider integration** — connect to client's BSS/API
4. **Content** — themes, layouts, journeys
5. **Go-live** — staging, conformance, production

## Phase 1: Discovery (1-2 weeks)

### Gather
- Client's industry (telco, insurance, ...)
- Number of subscribers / policyholders
- BSS / core system (Dialog BSS, AIA Core, ...)
- Authentication method (MSISDN, email, customer ID, ...)
- Branding assets (logo, colors, fonts)
- Regulatory requirements (data residency, retention)
- Languages (i18n requirements)

### Output
- Signed offboarding plan
- ADR for any platform extensions needed

## Phase 2: Tenant setup (1 week)

### 1. Create tenant record

In MongoDB `tenant_configs`:
```json
{
  "tenantId": "<client-id>",
  "industry": "<industry>",
  "displayName": "<Client Name>",
  "status": "ACTIVE",
  "createdAt": "2026-09-03T...",
  "createdBy": "platform-admin"
}
```

### 2. Configure environment

In Kubernetes, create:
- `<client-id>-secrets` (DB passwords, API keys)
- `<client-id>-configmap` (non-sensitive env)
- `<client-id>-namespace` (if isolation is per-tenant)

### 3. Set up database

For per-tenant isolation:
```sql
CREATE DATABASE selfcare_<client-id>;
GRANT ALL ON selfcare_<client-id>.* TO 'selfcare'@'%';
```

## Phase 3: Provider integration (2-4 weeks)

### 1. Implement provider

Create `industry-packs/<industry>/<client>/` Maven module.

Implement the relevant provider interfaces:
- `AuthProvider` (telco) or `InsuranceProvider` (insurance) or both
- `BalanceProvider` (telco) / `PolicyProvider` (insurance)
- `PaymentProvider` (telco)
- `NotificationProvider`

Each provider:
- Reads config from `client_integrations` at runtime
- Handles authentication (OAuth, API key, etc.)
- Implements retry, circuit breaker, timeout
- Returns mock data in dev mode

### 2. Configure integrations

In MongoDB `client_integrations`:
```json
{
  "tenantId": "<client-id>",
  "providerKey": "auth",
  "config": {
    "baseUrl": "https://bss.<client>.com",
    "clientId": "...",
    "clientSecret": "..."
  }
}
```

### 3. Test the integration

Use the conformance suite to verify:
- Auth flow (login, refresh, logout)
- Read flows (balance, policies, ...)
- Write flows (pay, claim, ...)
- Error handling (timeouts, 5xx, invalid input)

## Phase 4: Content (2-4 weeks)

### 1. Theme

In Selfcare Studio:
- Admin → Theme Designer
- Import client's brand guide
- Define color tokens, typography, spacing
- Test dark mode
- Preview on each industry page

### 2. Layouts

For each page (home, bills, profile, ...):
- Admin → Page Builder
- Drag widgets onto the canvas
- Configure props
- Preview on mobile + web
- Publish (DRAFT → APPROVED → PUBLISHED)

### 3. Journeys

Multi-step flows (e.g. "recharge", "submit claim", "change plan"):
- Admin → Journey Builder
- Define steps, transitions, error handling
- Add content templates
- Test full flow
- Publish

### 4. Notifications

For each event (claim submitted, payment received, ...):
- Admin → Notifications
- Define template (i18n)
- Choose channels (push, SMS, email)
- Test delivery

## Phase 5: Go-live (1-2 weeks)

### 1. Staging

- Deploy to staging with client-specific values
- Run full smoke test
- Run conformance suite
- Get client sign-off

### 2. Production

- Pre-prod checklist:
  - [ ] All conformance tests pass
  - [ ] Performance tests pass (load test with 10k concurrent users)
  - [ ] Security review complete
  - [ ] Disaster recovery tested
  - [ ] Client UAT sign-off
  - [ ] Communication plan ready

- Deploy:
  - [ ] Create production namespace
  - [ ] Apply secrets (from Vault)
  - [ ] Deploy services
  - [ ] Smoke test
  - [ ] Switch DNS / load balancer

### 3. Post-go-live

- [ ] Monitor for 48 hours
- [ ] Daily check-in for first week
- [ ] Weekly check-in for first month
- [ ] Hand off to support team

## Reference clients

| Client | Industry | Tenants | Notes |
|---|---|---|---|
| Dialog | Telco | dialog-lk | Reference telco client (Sri Lanka) |
| Hutch | Telco | hutch-lk | Sri Lanka telco |
| Airtel | Telco | airtel-lk | Sri Lanka telco |
| AIA | Insurance | aia-lk, aia-sg, aia-th, aia-my, aia-hk, aia-in | Multi-country insurance |

## Templates

- [templates/theme-template.json](../templates/theme-template.json)
- [templates/layout-template.json](../templates/layout-template.json)
- [templates/journey-template.json](../templates/journey-template.json)
- [templates/notification-template.json](../templates/notification-template.json)
