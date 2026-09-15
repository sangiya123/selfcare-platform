# Selfcare Platform — API Reference

This document covers the public HTTP API. Internal service-to-service calls
follow the same conventions but are not part of the public contract.

## Base URL

```
https://api.selfcare.io
```

All endpoints are prefixed with `/api/v1`. Industry-specific endpoints live
under `/api/v1/<industry>/...`.

## Authentication

Customer endpoints require a bearer token:
```
Authorization: Bearer <access_token>
```

The token is obtained from `/api/v1/auth/otp/verify` (customer) or
`/api/v1/auth/login` (admin).

### Tenant header

All requests must include:
```
X-Tenant-Id: <tenant_id>
```

The gateway rejects requests with missing or invalid tenant IDs (HTTP 400).

### Correlation ID

Optional but recommended:
```
X-Correlation-Id: <client-generated-uuid>
```

The gateway echoes this back in the response header for log correlation.

## Error responses

All errors follow the standard envelope:
```json
{
  "error": {
    "code": "INVALID_INPUT",
    "message": "Field 'amount' is required",
    "status": 400,
    "path": "/api/v1/payments/charge",
    "timestamp": "2026-09-03T15:42:00Z",
    "correlationId": "a1b2c3d4",
    "details": {
      "field": "amount",
      "reason": "required"
    }
  }
}
```

### Error codes

| Code | HTTP | Meaning |
|---|---|---|
| `UNAUTHENTICATED` | 401 | Missing or invalid token |
| `FORBIDDEN` | 403 | Token valid but lacks permission |
| `NOT_FOUND` | 404 | Resource doesn't exist |
| `CONFLICT` | 409 | Duplicate or invalid state |
| `INVALID_INPUT` | 400 | Request validation failed |
| `RATE_LIMITED` | 429 | Too many requests |
| `STEP_UP_REQUIRED` | 401 | Re-authentication needed |
| `UPSTREAM_ERROR` | 502 | Downstream provider error |
| `SERVICE_UNAVAILABLE` | 503 | Service down or circuit open |
| `GATEWAY_TIMEOUT` | 504 | Request exceeded overall deadline |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## Customer auth

### Request OTP
```http
POST /api/v1/auth/otp
Content-Type: application/json
X-Tenant-Id: dialog-lk

{
  "identifier": "+94771234567",
  "channel": "SMS"
}
```

**Response** `202 Accepted`:
```json
{
  "correlationId": "otp-a1b2c3",
  "expiresAt": "2026-09-03T15:47:00Z",
  "expiresIn": 300
}
```

### Verify OTP
```http
POST /api/v1/auth/otp/verify
Content-Type: application/json
X-Tenant-Id: dialog-lk

{
  "identifier": "+94771234567",
  "code": "123456",
  "correlationId": "otp-a1b2c3"
}
```

**Response** `200 OK`:
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "rt-abc123...",
  "sessionId": "sess-xyz",
  "expiresIn": 900,
  "refreshExpiresIn": 2592000,
  "tokenType": "Bearer",
  "user": {
    "userId": "MSISDN:+94771234567",
    "tenantId": "dialog-lk",
    "primaryConnectionId": "CONN-001"
  }
}
```

### Refresh token
```http
POST /api/v1/auth/refresh
Content-Type: application/json
X-Tenant-Id: dialog-lk

{
  "refreshToken": "rt-abc123..."
}
```

**Response** `200 OK`: same shape as Verify OTP, with new tokens.

The old refresh token is invalidated. Reuse of an old refresh token
triggers full session revocation (replay detection, ADR-011).

### Sign out
```http
POST /api/v1/auth/signout
Authorization: Bearer <token>
```

**Response** `204 No Content`.

## Dashboard (BFF)

### Get home dashboard
```http
GET /api/v1/dashboard/home?connectionId=CONN-001
Authorization: Bearer <token>
X-Tenant-Id: dialog-lk
```

**Response** `200 OK`:
```json
{
  "data": {
    "correlationId": "dash-a1b2c3",
    "elapsedMs": 412,
    "overallStatus": "PARTIAL",
    "widgets": {
      "balance": {
        "widgetId": "balance",
        "status": "SUCCESS",
        "data": { "amount": 1250.00, "currency": "LKR" },
        "elapsedMs": 87
      },
      "banners": {
        "widgetId": "banners",
        "status": "TIMEOUT",
        "errorMessage": "Widget timed out after 300ms",
        "retryable": true
      }
    }
  }
}
```

### Refresh single widget
```http
GET /api/v1/dashboard/widgets/balance?connectionId=CONN-001
```

**Response** `200 OK`: single widget result.

## Bills

### List bills
```http
GET /api/v1/bills?connectionId=CONN-001&status=OVERDUE
Authorization: Bearer <token>
```

**Response** `200 OK`:
```json
{
  "data": [
    {
      "billId": "BILL-001",
      "connectionId": "CONN-001",
      "issueDate": "2026-08-01",
      "dueDate": "2026-08-31",
      "totalAmount": 5500.00,
      "currency": "LKR",
      "status": "OVERDUE"
    }
  ]
}
```

### Pay bill
```http
POST /api/v1/bills/{billId}/pay
Authorization: Bearer <token>
X-Idempotency-Key: <client-generated-uuid>

{
  "amount": 5500.00,
  "paymentMethodId": "PM-001"
}
```

**Response** `200 OK`:
```json
{
  "data": {
    "transactionId": "TXN-abc123",
    "status": "PENDING",
    "amount": 5500.00,
    "currency": "LKR"
  }
}
```

## Insurance (industry: insurance)

### List policies
```http
GET /api/v1/insurance/policies
Authorization: Bearer <token>
X-Tenant-Id: aia-lk
```

**Response** `200 OK`:
```json
{
  "data": [
    {
      "policyId": "AIA-POL-001",
      "productType": "LIFE",
      "policyholderId": "CUST-001",
      "sumAssured": 5000000.00,
      "currency": "LKR",
      "premiumAmount": 12500.00,
      "premiumFrequency": "MONTHLY",
      "status": "ACTIVE",
      "startDate": "2024-01-01",
      "nextPremiumDue": "2026-10-01"
    }
  ]
}
```

### Submit claim
```http
POST /api/v1/insurance/claims
Authorization: Bearer <token>
X-Tenant-Id: aia-lk
X-Idempotency-Key: <client-generated-uuid>

{
  "policyId": "AIA-POL-001",
  "claimType": "HOSPITALISATION",
  "incidentDate": "2026-08-15",
  "amount": 250000.00,
  "documents": ["doc-1", "doc-2"]
}
```

**Response** `202 Accepted`:
```json
{
  "data": {
    "claimId": "AIA-CLM-001",
    "status": "SUBMITTED",
    "submittedAt": "2026-09-03T15:42:00Z"
  }
}
```

## Config (server-driven UI)

### Get manifest
```http
GET /api/v1/config/manifest?experience=home&profileKey=mobile_prepaid
If-None-Match: "dialog-lk:184"
X-Tenant-Id: dialog-lk
```

**Response** `200 OK` (or `304 Not Modified` if etag matches):
```json
{
  "schemaVersion": "2.0",
  "configVersion": 184,
  "tenant": "dialog-lk",
  "experience": "home",
  "sections": [...],
  "theme": {...},
  "navigation": {...}
}
```

## Pagination

List endpoints use cursor-based pagination:
```http
GET /api/v1/bills?cursor=eyJpZCI6IkJJTEwtMDAxIn0&limit=20
```

**Response** includes:
```json
{
  "data": [...],
  "pagination": {
    "nextCursor": "eyJpZCI6IkJJTEwtMDIxIn0",
    "hasMore": true
  }
}
```

## Rate limits

| Endpoint | Limit |
|---|---|
| `/api/v1/auth/**` | 30 req/min per IP (no token) |
| `/api/v1/dashboard/**` | 60 req/min per user |
| `/api/v1/payments/**` | 10 req/min per user |
| All other reads | 120 req/min per user |

Rate-limited responses include `Retry-After` header.

## Versioning

The API follows `/api/v1/...` versioning. Breaking changes bump to v2.
Deprecation is announced 6 months before removal.
