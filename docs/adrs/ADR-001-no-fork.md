# ADR-001: One Product, Zero Client Forks

**Status**: ACCEPTED
**Date**: 2026-09-03
**Deciders**: selfcare Architecture Council

## Context

Dialog, Hutch, and Airtel each have separate selfcare apps and backends with significant overlap.
The Dialog codebase alone contains 31,000+ files. Hutch and Airtel add more.

Each client has its own brand, navigation, and integration layer, but the underlying
**selfcare capabilities** (dashboard, balance, usage, billing, payments, packages,
notifications, support) are essentially the same within an industry.

## Decision

**One Selfcare Product Kernel** that serves clients across multiple industries:

- Shared source code (React Native + TypeScript mobile, React + TypeScript admin,
  Java 25 + Spring reactive backend)
- **Industry Packs** for per-vertical provider implementations
  (telco pack, insurance pack, travel pack, ...)
- **Client modules** within each industry pack for per-tenant implementations
  (Dialog, Hutch, Airtel under the telco pack; AIA, Allianz, Prudential under
  the insurance pack)
- **Tenant Configuration** for per-client settings (theme, layout, navigation,
  content, journeys, integrations)
- **Client Data Planes** for isolated runtime environments (databases, secrets, ...)

**No client source forks.** The same binary renders each client's experience based on:
- `X-Tenant-Id` header from API Gateway
- Tenant config (industry, tenantType, country, ...) from `config-tenant-service`
- Industry pack from `industry-packs/<industry>/<client>/`
- Integration config from `client_integrations` collection

## Terminology

| Term | Meaning |
|---|---|
| Client / Tenant | A business using selfcare (Dialog, AIA, ...) |
| Industry | TELCO, INSURANCE, TRAVEL, BANKING, ... |
| Tenant type | OPERATOR, INSURER, MVNO, AIRLINE, ... |
| Industry pack | Per-vertical provider implementation (telco pack, insurance pack) |
| Client module | A specific company under an industry pack (Dialog under telco, AIA under insurance) |

## Rationale

### Why this matters

1. **Operational efficiency** — One codebase to maintain, test, secure, and upgrade
2. **Time to market** — New clients onboard through config, not code
3. **Quality** — All clients get all features, not just "their version"
4. **Security** — Security fixes apply to all clients simultaneously
5. **Innovation** — One platform team, not three (or seven)
6. **Multi-industry leverage** — A new industry (e.g. travel) can be added by writing
   one industry pack, without disturbing telco or insurance

### Why not the alternatives

- **Per-client repositories** (status quo): High cost, low reuse, drift
- **Shared library, multiple products**: Still per-client product, still drift
- **Client-specific plugins in shared core**: Same idea as industry pack, but with
  greater coupling

## Implementation

### Mobile kernel
- React Native + TypeScript app
- `ConfigSDK` fetches `ExperienceManifest` from `config-tenant-service`
- `ComponentRegistry` maps `componentId` -> React Native component
- `LayoutRenderer` recursively renders sections from manifest
- `ActionEngine` executes config-defined actions
- `ThemeEngine` resolves design tokens from tenant theme
- Terminology layer resolves industry-specific labels (subscriber vs policyholder)

### Backend services
- Microservices: identity, account, config, dashboard BFF, product, usage,
  billing, payment, notification, content, journey, reporting, AI, audit
- Per-industry BFFs (telco, insurance, ...) that aggregate industry-specific APIs
- `ApiAdapterRegistry<T>` provides tenant-specific adapter implementations
- Provider pattern: each capability has a canonical interface, clients provide impls
- The kernel is industry-neutral; industry-specific interfaces live in their
  industry pack

### Industry pack structure
```
industry-packs/
├── telco/                              # Telco industry pack
│   ├── pom.xml
│   ├── dialog/                         # Dialog (LK) — telco client
│   │   ├── pom.xml
│   │   ├── theme/dialog-theme.json
│   │   ├── layout/                     # Dialog-specific layouts
│   │   └── providers/                  # DialogAuthProvider, DialogBalanceProvider, ...
│   ├── hutch/                          # Hutch (LK)
│   └── airtel/                         # Airtel (LK)
└── insurance/                          # Insurance industry pack
    ├── pom.xml
    └── aia/                            # AIA — multi-country insurance client
        ├── pom.xml
        ├── theme/                      # AIA theme per country
        └── providers/                  # AIAInsuranceProvider (Policy, Claim, Beneficiary, Premium)
```

### Industry terminology is per pack

The platform core uses **industry-neutral** terms. Each industry pack contributes
its own vocabulary:

| Generic (platform) | Telco | Insurance |
|---|---|---|
| Tenant | Operator | Insurer |
| End user | Subscriber | Policyholder |
| Product | Offer | Policy |
| Account | Connection / MSISDN | Policy |
| Top-up | Recharge | Pay premium |
| Issue | Support ticket | Claim |

When the platform renders a telco client (Dialog), the UI uses "subscriber",
"MSISDN", "recharge". When it renders AIA, it uses "policyholder", "policy",
"premium". Same kernel, different labels.
