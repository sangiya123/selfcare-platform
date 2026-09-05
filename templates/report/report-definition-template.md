# Report Definition — <Report name>

| Field | Value |
|---|---|
| **Report ID** | R-NNNN |
| **Category** | sales / usage / billing / payment / customer / support / compliance / operational / executive / financial / ai |
| **Sub-category** | … |
| **Owner** | name + email (business) + name + email (technical) |
| **Status** | DRAFT / APPROVED / IN_PROGRESS / PUBLISHED / DEPRECATED |
| **Version** | semver |
| **Compliance** | PCI-DSS / GDPR / SOX / regulatory (specify) |

## Purpose

What business question does this report answer? Who is the audience?

> Example: "Daily revenue by product and payment channel, for finance
> reconciliation."

## Audience

- **Primary:** Finance team, daily reconciliation
- **Secondary:** Product team, weekly review
- **Cadence:** daily / weekly / monthly / on-demand

## Inputs

| Input | Source | Required scope | Masking |
|---|---|---|---|
| Payment records | `payment-service` MySQL | tenant, date range | MSISDN masked |
| Product catalog | `product-service` MySQL | tenant | — |
| Customer account | `account-entitlement-service` | tenant, segment | NIC masked |
| Channel mapping | `config-tenant-service` MongoDB | tenant, env | — |

## Query

Reference the canonical query spec in `reporting-service/catalog/`.

```yaml
id: daily-revenue-by-channel
category: financial
query:
  select:
    - payment_date
    - channel
    - product_id
    - sum(amount) as total_revenue
  from: payment
  where: status='COMPLETED' AND tenant_id=:tenantId
  group_by: [payment_date, channel, product_id]
  order_by: payment_date DESC
parameters:
  - name: tenantId
    type: string
    required: true
  - name: fromDate
    type: date
    required: true
  - name: toDate
    type: date
    required: true
columns:
  - payment_date: date
  - channel: string
  - product_id: string
  - total_revenue: decimal
```

## Output

- **Format:** CSV / XLSX / PDF / Parquet
- **Delivery:** email / S3 / webhook / API
- **Schedule:** daily at 02:00 UTC
- **Retention:** 90 days online, 7 years in cold storage
- **Encryption:** at rest (KMS), in transit (TLS)

## Authorization

Who can run this report?

| Role | Scope | Approval required |
|---|---|---|
| Finance analyst | Own tenant | No |
| Tenant admin | Own tenant | No |
| Operator admin | Cross-tenant | Yes — sponsor approval |
| Auditor | Cross-tenant, read-only | Yes — compliance approval |

## PII / Masking

- **Customer identifiers:** masked (`+947****56`)
- **NIC / passport:** never included unless explicitly approved
- **Account numbers:** last 4 only
- **Address:** city-level only

## Performance

- **Data volume:** ~500k rows/day per tenant
- **Expected runtime:** < 30s for 30-day range
- **Indexing:** `(tenant_id, payment_date, status)` composite index
- **Materialization:** nightly batch, cached for 24h

## Testing

- **Unit test:** query parser + parameter validation
- **Integration test:** real DB seed, verify row counts
- **Performance test:** 1M rows, < 60s
- **Compliance test:** verify PII masking applied
- **Acceptance test:** sample run by report owner, sign off

## Rollout

- Publish to Studio `report-catalog`
- Available to authorized roles immediately
- Add to finance team's scheduled report list
- Document in operator runbook

## Change Management

- **Schema changes to underlying data:** require coordinated rollout
  with `account-service` and `payment-service` teams
- **New columns / fields:** additive only, no breaking changes
- **Deprecation:** 90-day notice, then archive (still queryable but
  not in catalog)
