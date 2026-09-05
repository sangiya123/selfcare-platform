# ADR-018: PII Classification & Masking Strategy

## Status
Accepted — 2026-09-04

## Context
The platform processes PII across all tenant types:
- MSISDN, NIC/Passport, Email, Phone (telco)
- Policy number, Claim number, Premium amount (insurance)
- Payment card data (all industries)
- Address, date of birth (all)

The security spec mandates:
- PII masking in logs/telemetry
- Field-level masking in reports
- AI/RAG never sends raw PII to model
- Subject access request (SAR) workflows
- Retention per data class

## Decision
We implement a **centralized PII classification system**:

### 1. PII Classes (canonical)
Defined in `PiiClass` enum:
- `EMAIL` — john@example.com
- `PHONE` — international phone
- `MSISDN` — telco phone (+94 77 1234567)
- `NATIONAL_ID` — NIC/passport
- `PAYMENT_CARD` — full PAN
- `POLICY_NUMBER` — insurance policy
- `ACCOUNT_NUMBER` — bank account / connection account
- `IP_ADDRESS` — IPv4/IPv6
- `JWT` / `SECRET_KEY` — tokens and secrets
- `CUSTOMER_NAME` — full name
- `DATE_OF_BIRTH` — DOB
- `ADDRESS` — postal address

### 2. Masking strategies
- `REPLACE_TAG` — `<MSISDN>`, `<EMAIL>` (default for logs)
- `PARTIAL` — `***-***-1234` (last 4 of MSISDN/card)
- `HASH` — SHA-256 first 8 chars (for analytics that need linking)
- `REDACT` — complete removal
- `NONE` — no masking (allowed only for explicit allow-list)

### 3. Three enforcement points
- **Logging**: `PiiMaskingService.maskForLog(message)` — used in all log statements
- **Reports**: `ReportRenderer` masks columns by header/PII class
- **AI**: `PiiMaskingService.maskForAi(message, useCase)` — per-use-case policy

### 4. Detection
- Regex-based pattern matcher for each class
- `maskMap(input, rules)` — returns `{ maskedText, classMap }`

### 5. Configuration
- Per-tenant override via `PiiPolicy` document in MongoDB
- Default: `REPLACE_TAG` for all classes
- Industry defaults:
  - TELCO: `MSISDN` = `PARTIAL`, `EMAIL` = `REPLACE_TAG`
  - INSURANCE: `POLICY_NUMBER` = `PARTIAL`, `NAME` = `REPLACE_TAG`
  - BANKING: `ACCOUNT_NUMBER` = `PARTIAL`, `PAYMENT_CARD` = `REDACT`

### 6. Subject access request (SAR)
- `POST /api/v1/admin/privacy/sar/{customerId}` — generates all data for a customer
- Returns a redacted bundle of all records (per operator policy)
- Audit-logged

### 7. Retention
- Configured per PII class per tenant
- `PiiRetentionJob` (in audit-service) runs daily, purges records past retention
- Audit log retains "this data was deleted" forever (without the actual data)

## Consequences

Positive:
- Consistent PII handling across the platform
- Compliant with regional data protection laws
- AI never sees raw PII by default
- SAR workflow built-in

Negative:
- Pattern matcher may have false positives
- Tenant must opt-in to stricter rules (or live with safe defaults)
- Masking can make debugging harder

## Compliance
- Spec § "Privacy and data residency"
- AI governance: prompt-injection defense + data minimization
- NFR-PRIVACY: operator data planes isolated
- NFR-AUDIT: SAR requests audited
