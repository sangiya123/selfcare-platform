# Insurance Industry Pack

This document covers how the platform extends to the insurance vertical,
with AIA as the reference client.

> **Scope note**: AIA's full frontend/backend source code was **not** available
> for this analysis. This documentation covers the **product architecture**
> integration of insurance into the platform. Implementation details that
> would require AIA's full source (e.g. their internal domain model, BSS
> integration patterns, exact claim submission flow) are described at the
> contract level and integrated via the [InsuranceProvider interface](../industry-packs/insurance/aia/providers/).

## Concepts

| Insurance concept | Maps to platform concept |
|---|---|
| Policyholder | Customer (`userId` in identity service) |
| Policy | Account-like entity (own table) |
| Premium | Recurring payment (in billing-service) |
| Claim | One-time event (in journey-service) |
| Beneficiary | Linked entity (in account-entitlement-service) |
| Policy relationship | Primary identity's linked policies (analog of ADR-006) |

## AIA tenants

AIA is a multi-country insurance group. The platform treats each country
as a separate tenant:

| Tenant | Country | Currency |
|---|---|---|
| `aia-lk` | Sri Lanka | LKR |
| `aia-sg` | Singapore | SGD |
| `aia-th` | Thailand | THB |
| `aia-my` | Malaysia | MYR |
| `aia-hk` | Hong Kong | HKD |
| `aia-in` | India | INR |

A single `AIAInsuranceProvider` implementation handles all 6 tenants —
per-tenant configuration (URLs, credentials) is fetched from
`client_integrations` at runtime.

## Insurance widgets

| Widget | Purpose |
|---|---|
| `InsurancePolicyCard` | Show policy summary, status, premium |
| `InsurancePremiumDue` | Highlight upcoming/overdue premium |
| `InsuranceClaimCard` | Show claim status timeline |
| `InsuranceBeneficiaryCard` | Show beneficiaries + relationship |

These widgets are registered in the mobile app's `ComponentRegistry` and
are available for any tenant with `industry = "insurance"`.

The dashboard-bff renders these widgets only when the tenant's industry is
"insurance" (`InsurancePoliciesWidget.isAvailable()`).

## Insurance service architecture

```
Insurance BFF (insurance-service, port 8096)
├── Policies endpoint        (list, get, renew)
├── Claims endpoint          (list, submit, get)
├── Premiums endpoint        (summary, history)
├── Beneficiaries endpoint   (list, add, remove)
└── Provider adapter (InsuranceProvider)
    └── AIAInsuranceProvider  (implements 18 methods)
```

## AIA integration

### Authentication
AIA APIs use OAuth2 client_credentials. The AIA provider caches tokens
per tenant (refreshed 5 min before expiry).

### Mock mode
For development, `selfcare.aia.mock-mode=true` returns realistic mock data
without calling AIA. This keeps the seeder URLs (`https://aia-mock.selfcare.io`)
working in dev.

### Real API integration
Set `selfcare.aia.mock-mode=false` and configure:
```yaml
selfcare:
  aia:
    mock-mode: false
    timeout-ms: 5000
    retry-attempts: 3
```

In `client_integrations`:
```json
{
  "tenantId": "aia-lk",
  "providerKey": "insurance",
  "config": {
    "baseUrl": "https://api.aia.com/lk/v1",
    "clientId": "...",
    "clientSecret": "...",
    "oauthUrl": "https://api.aia.com/oauth/token"
  }
}
```

## Adding a new insurance client

1. Create `industry-packs/insurance/<new-client>/` Maven module
2. Implement `InsuranceProvider` interface
3. Register with `@RegisterAdapter(tenantId = "<new-client>")`
4. Add to `client_integrations` seed data
5. Create widget variants if needed (e.g. different claim types)

No platform code changes are required.

## Cross-industry vs insurance-specific

The platform kernel handles auth, billing (premiums), notifications (claim
updates), and content (FAQs). Insurance-specific logic stays in the
insurance industry pack:

- Policy domain model
- Claim submission workflow
- Beneficiary relationship rules
- AIA-specific API integration
