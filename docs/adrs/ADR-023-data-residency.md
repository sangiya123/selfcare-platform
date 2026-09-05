# ADR-023: Data Residency and Cross-Border Transfer

## Status
Accepted — 2026-09-04

## Context
The platform serves operators across multiple regulatory regimes:

- **Sri Lanka, Bangladesh, Pakistan, Nepal** — local data protection laws,
  customer data must stay in-country
- **EU operators** — GDPR, data subject rights, Schrems-II
- **Insurance underwriters** — additional data residency from reinsurance
  contracts and actuarial requirements
- **Banking clients** — PCI-DSS, often country-specific data localisation

The spec requires:
- Per-tenant data residency
- Audit trail of cross-border requests
- Region pinning for AI inference
- Tenant-controlled region choice (with operator-policy guardrails)

## Decision
We adopt a **region-pinned, tenant-isolated** model:

### 1. Tenant regions
Each tenant config has:
```yaml
tenant:
  id: dialog-lk
  region: lk  # ISO 3166-1 alpha-2 lowercase
  residency:
    primary: lk
    disasterRecovery: lk  # same country, different city
    aiInference: lk       # LLM must run in-region
  encryption:
    atRest: AES-256-GCM   # KMS key per region
    inTransit: TLS 1.3
    keyManagement: vault  # or aws-kms, gcp-kms per operator
```

### 2. Per-tenant data plane
- Database (MySQL, MongoDB) deployed in tenant's region
- Redis cluster pinned to region
- Object storage bucket in tenant's region
- Search index in tenant's region

### 3. Backup rules
- Backups stay in-region (no cross-border)
- DR replication only to same-country disaster site
- For operators with no in-country DR site: same region, multi-AZ only

### 4. AI inference region pinning
- LLM gateway enforces `aiInference` region
- If a model is requested in a region where it's not deployed:
  - Falls back to a region-pinned model
  - Logs warning in audit
  - Surfaces in admin UI

### 5. Cross-border requests
- Forbidden by default
- If a service MUST call across regions (rare), the request:
  - Logs to `cross_border_audit` with full reason
  - Is blocked unless `crossBorderAllowList` permits
  - Cannot carry PII (PiiMaskingService strips first)

### 6. SAR (Subject Access Request)
- `POST /api/v1/admin/privacy/sar/{customerId}` collects ALL data for a customer
- Scoped to tenant's region (cannot span regions)
- Bundle is signed and encrypted before download

### 7. Compliance attestations
- Per-tenant SOC2 / ISO27001 evidence pack generated annually
- Encryption-at-rest proof: KMS audit logs
- Encryption-in-transit proof: TLS scan results

## Implementation
- `TenantRegion` enforced by `RegionEnforcementFilter` in gateway
- `ResidencyPolicy` per tenant in MongoDB
- Cross-border call intercepted by `CrossBorderInterceptor` (AOP)

## Consequences

Positive:
- Compliance with regional data protection laws
- Clear audit story
- Predictable performance (in-region latency)

Negative:
- Multi-region operational complexity
- Cannot use global services that require cross-region replication
- Backup/DR cost per region

## Compliance
- GDPR Articles 44-50 (cross-border transfers)
- NFR-PRIVACY: per-tenant data plane isolated
- Spec § "Privacy and data residency"
