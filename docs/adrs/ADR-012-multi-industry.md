# ADR-012: Industry Pack Abstraction Layer

**Status**: ACCEPTED
**Date**: 2026-09-03
**Deciders**: selfcare Architecture Council

## Context

The Selfcare Platform serves multiple industries:
- **Telco** (Dialog, Hutch, Airtel) — subscriber management, recharge, bundles
- **Insurance** (AIA) — policyholder management, premiums, claims
- **Future**: Banking, Travel, Healthcare

Each industry has fundamentally different:
- Domain vocabulary (MSISDN vs Policy vs Account)
- Provider integrations (BSS vs Insurance core systems)
- Regulatory requirements
- Dashboard widgets and layout patterns

We need a **platform** that supports all of these from a single codebase,
configurable per tenant.

## Decision

**Industry Pack abstraction layer.** The platform kernel is industry-agnostic.
Each industry has a dedicated **Provider Pack** that implements industry-specific
interfaces. Tenant config binds which pack to use.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                  selfcare Platform                     │
│                                                      │
│  ┌────────────────────────────────────────────────┐ │
│  │              Industry Pack: Telco               │ │
│  │                                                 │ │
│  │  DialogAuthProvider    → Dialog BSS API       │ │
│  │  HutchAuthProvider      → Hutch BSS API        │ │
│  │  AirtelAuthProvider     → Airtel BSS API       │ │
│  │  TelcoBalanceProvider                          │ │
│  │  TelcoRechargeProvider                        │ │
│  │  TelcoUsageProvider                            │ │
│  │  TelcoEntitlementService                       │ │
│  └────────────────────────────────────────────────┘ │
│                                                      │
│  ┌────────────────────────────────────────────────┐ │
│  │            Industry Pack: Insurance             │ │
│  │                                                 │ │
│  │  AIAAuthProvider         → AIA customer API   │ │
│  │  AIAProvider             → AIA core system    │ │
│  │  InsurancePolicyProvider                       │ │
│  │  InsuranceClaimProvider                        │ │
│  │  InsuranceEntitlementService                   │ │
│  └────────────────────────────────────────────────┘ │
│                                                      │
│  ┌────────────────────────────────────────────────┐ │
│  │              Platform Kernel (industry-neutral) │ │
│  │                                                 │ │
│  │  CustomerIdentityService  (auth: OTP, JWT)     │ │
│  │  AccountEntitlementService (authorize)        │ │
│  │  BillingService          (invoices, payments) │ │
│  │  NotificationService      (channels, templates)│ │
│  │  JourneyService           (multi-step flows)   │ │
│  │  DashboardBFF             (widget aggregator)   │ │
│  └────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

## Provider Registry pattern

```java
public interface ApiAdapter {
    String tenantId();
    String industry();
}

public class ApiAdapterRegistry<T extends ApiAdapter> {
    private final Map<String, T> adapters = new ConcurrentHashMap<>();

    public void register(T adapter) {
        adapters.put(adapter.tenantId(), adapter);
    }

    public Optional<T> getAdapter(String tenantId) {
        return Optional.ofNullable(adapters.get(tenantId));
    }
}
```

Industry packs register their adapters at startup via `@RegisterAdapter`.

## Industry-specific interfaces

### Telco
```java
public interface BalanceProvider extends ApiAdapter {
    Balance getBalance(String msisdn);
    Mono<Balance> getBalanceAsync(String msisdn);
}

public interface RechargeProvider extends ApiAdapter {
    RechargeResult recharge(String msisdn, BigDecimal amount, String pin);
}

public interface TelcoEntitlement {
    boolean canRecharge(String actorConnectionId, String targetMsisdn);
    boolean canViewUsage(String actorConnectionId, String targetMsisdn);
}
```

### Insurance
```java
public interface InsuranceProvider extends ApiAdapter {
    Policy getPolicy(String policyNumber);
    List<Claim> getClaims(String policyNumber);
    PremiumSummary getPremiumSummary(String policyNumber);
}

public interface InsuranceEntitlement {
    boolean isPolicyholder(String connectionId, String policyNumber);
    boolean isBeneficiary(String connectionId, String policyNumber);
}
```

## Tenant config binding

```json
// MongoDB: tenant_configs collection
{
  "tenantId": "dialog-lk",
  "industry": "telco",
  "providerPack": "telco/dialog",
  "providerBindings": {
    "auth": "DialogAuthProvider",
    "balance": "TelcoBalanceProvider",
    "recharge": "DialogRechargeProvider",
    "usage": "TelcoUsageProvider"
  },
  "allowedConnectionTypes": ["PREPAID", "POSTPAID"],
  "crossConnectionEnabled": true,
  "stepUpThreshold": 10000,
  "currency": "LKR",
  "locale": "en-LK"
}
```

## Layout schema variation

Layout documents can have industry-specific section types:

```json
// Telco layout
{
  "sections": [
    { "component": "BalanceCard", "props": { "showCurrency": true } },
    { "component": "UsageWidget", "props": { "cycleResetDay": 1 } },
    { "component": "BundleCarousel", "props": { "category": "data" } }
  ]
}

// Insurance layout
{
  "sections": [
    { "component": "PolicyCard", "props": { "showClaims": true } },
    { "component": "PremiumSummary", "props": { "nextDueDate": "{{premium.nextDue}}" } },
    { "component": "ClaimStatus", "props": { "showHistory": 5 } }
  ]
}
```

The component registry is shared; industry-specific components register
themselves (e.g. `PolicyCard` registers in the Insurance Pack, not the kernel).

## Consequences

### Positive
- Single platform, multiple industries — no forks
- New industry = new provider pack, no core changes
- Clear ownership boundaries per industry
- Provider implementations are isolated and testable

### Negative
- Cross-industry features require kernel extensions
- Multiple provider packs to maintain and test
- Industry-specific terminology must be mapped in the kernel

## Adding a new industry

1. Create `industry-packs/insurance-aia/` Maven module
2. Implement `ApiAdapter` + industry-specific interfaces
3. Register adapters with `@RegisterAdapter` at startup
4. Add industry to `Industry` enum
5. Configure tenant in admin UI (industry = "insurance", providerPack = "insurance-aia")
6. No platform code changes needed
