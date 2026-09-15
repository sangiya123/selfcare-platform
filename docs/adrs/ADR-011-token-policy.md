# ADR-011: Token Lifecycle, Replay Prevention, and Per-Tenant TTL Policy

**Status**: ACCEPTED (resolves prior OPEN review)
**Date**: 2026-09-03
**Deciders**: selfcare Architecture Council, Security Team, Product

## Context

The selfcare platform serves millions of mobile and web users across multiple
tenants. We need secure, scalable authentication that:
- Works across 19 microservices without shared session state
- Detects and prevents token replay attacks
- Supports cross-connection operations with step-up auth
- Provides audit trail for compliance
- Honors the business-stated **42-day access / 7-month refresh** lifetime
  request while keeping the platform defensible from a security standpoint

The planning doc (`09_roadmap_migration/03_ADR_Key_Decisions.md`) marked this
ADR as OPEN because the requested 42-day access / 7-month refresh lifetimes
needed to be reconciled with the source code defaults and a documented
threat model. This revision **resolves** the OPEN status.

## Decision

1. **RS256 JWT with refresh token rotation, replay detection, and Redis-backed
   blacklist** (algorithm unchanged from prior draft).
2. **Per-tenant token TTL policy is stored in MongoDB** (tenant config
   collection), **not** in environment variables. This is consistent with
   ADR-001 (no per-tenant forks, no per-tenant env files) and ADR-004
   (Mongo is config source of truth).
3. **Default (platform baseline)** is **24h access / 30d refresh** — strictly
   shorter than the business request, justified by the threat model in
   §"Threat model and TTL trade-offs" below.
4. **Tenants that explicitly opt in** may set a longer policy (up to the
   business-stated maximum of **42d access / 7mo refresh**), but must satisfy
   the compensating controls in §"Compensating controls for long-lived
   tokens" (device binding, mandatory refresh-token rotation, step-up for
   sensitive actions, and an audit justification).
5. **All TTL values are read at token-issuance time from the tenant's policy
   record**, not from JWT claims, so a policy change takes effect on the
   next issuance without invalidating live tokens.

## Token architecture

```
Access Token (JWT, RS256 signed)
├── iss: selfcare-platform
├── sub: connectionId
├── aud: tenantId
├── tenantId: dialog-lk
├── scope: [balance:read, bills:read]
├── primaryConnectionId: CONN-001
├── linkedConnectionIds: [CONN-002, CONN-003]
├── amr: [pwd]           // Authentication methods
├── sid: <session-id>    // Session ID for step-up
└── exp: per-tenant access-token-seconds (24h default, up to 42d)

Refresh Token (opaque, stored in Redis)
├── tokenHash: sha256(refresh-token)
├── connectionId: CONN-001
├── sessionId: <uuid>
├── issuedAt: 2026-09-03T10:00:00Z
├── expiresAt: per-tenant refresh-token-seconds (30d default, up to 7mo)
└── rotatedFrom: null | <previous-token-hash>
```

## Lifecycle

1. **Login** — OTP or password verified → load tenant token policy →
   issue access token (per-tenant TTL) + refresh token (per-tenant TTL)
2. **Use** — Mobile app sends access token in `Authorization: Bearer <token>`
3. **Refresh** — Before expiry, app calls `POST /auth/refresh` with refresh
   token. Policy is re-loaded on each issuance; mid-life TTL change takes
   effect on the next refresh.
4. **Rotate** — New refresh token issued, old one blacklisted (replay
   detection)
5. **Logout** — Both tokens blacklisted

## Threat model and TTL trade-offs

Why 24h / 30d by default, and not 42d / 7mo? The threat model treats a leaked
access token as the primary concern. A 42-day bearer is acceptable **only** if
the platform can detect compromise quickly, which the default policy
guarantees via:

- Refresh-token rotation (every refresh invalidates the previous one)
- Replay detection (a reused refresh token revokes the whole session)
- Per-session, per-device last-activity tracking in Redis
- Step-up auth for any high-value action (cross-connection payment,
  profile/security change, high-value transaction)

The 7-month refresh window is acceptable for mobile apps (the
`device-bound` + `device-fingerprint` claim pins the refresh token to the
device that originally received it, so a stolen refresh token alone is
insufficient for reuse from a different device).

| Vector | 24h access | 42d access |
|---|---|---|
| Stolen access token misuse window | 24h | 42d |
| Stolen refresh token (rotation) | Reuse ⇒ session revoked | Reuse ⇒ session revoked (same) |
| Stolen refresh token (no reuse, slow exfil) | Caught at next refresh | Caught at next refresh (same) |
| Forced logout propagation | 24h max | 42d max |

The 42d option is therefore acceptable **only when paired with the
compensating controls below**. The default is shorter because the default
tenants are telco carriers with regulated data, not insurance clients with
long-lived customer relationships.

## Compensating controls for long-lived tokens

A tenant that sets `accessTokenSeconds > 86400` (24h) MUST also have:

1. **Device binding** — refresh tokens store the device fingerprint;
   refresh from a new device requires re-authentication (OTP).
2. **Mandatory refresh-token rotation** — every refresh issues a new
   token; reuse revokes the session (already in place platform-wide).
3. **Step-up auth for sensitive actions** — already enforced by
   `StepUpService` (payment, profile, security changes).
4. **Audit justification** — `tenant_token_policy.justification` is a
   required field when opting in to a longer policy, and the
   `AuditService` records a `CONFIG` event every time the policy is
   changed.
5. **Compromised-credential monitoring** — if `CompromisedCredentialService`
   reports a hit on the user's `sub` or `primaryConnectionId`, the
   session is revoked regardless of token TTL.

The `customer-identity-service` reads the policy on each issuance. The
service **refuses** to issue a token with `accessTokenSeconds > 42d` or
`refreshTokenSeconds > 7mo` and logs a security warning.

## Per-tenant policy (DB-backed, not env)

The policy is a MongoDB document in the `tenant_config` collection:

```javascript
{
  _id: "tenant-token-policy:dialog-lk",
  tenantId: "dialog-lk",
  accessTokenSeconds: 86400,           // 24h
  refreshTokenSeconds: 2592000,        // 30d
  deviceBindingRequired: true,
  rotationEnforced: true,
  stepUpThresholdSeconds: 10000.00,    // payment threshold
  maxInactiveSessionSeconds: 1800,     // 30 min idle logout
  justification: null,                 // required if TTL > defaults
  policyVersion: 3,
  updatedAt: ISODate(),
  updatedBy: "admin@selfcare"
}
```

For tenants that want the 42d/7mo policy:

```javascript
{
  _id: "tenant-token-policy:long-lived-insurer",
  tenantId: "long-lived-insurer",
  accessTokenSeconds: 3628800,         // 42d
  refreshTokenSeconds: 18144000,       // 7mo
  deviceBindingRequired: true,
  rotationEnforced: true,
  stepUpThresholdSeconds: 10000.00,
  maxInactiveSessionSeconds: 1800,
  justification: "Long-lived policy clients expect infrequent re-auth; mandatory device binding + rotation are enforced.",
  policyVersion: 1,
  updatedAt: ISODate(),
  updatedBy: "ciso@selfcare"
}
```

Per the security principle **"no security-sensitive value comes from
environment variables when it can come from config"**, this ensures a
tenant change is auditable and reversible without redeployment.

## Replay detection

Every refresh token has a `tokenHash` in Redis. When refresh is called:
1. Hash incoming token
2. Check Redis: `token:<hash>` exists?
3. If yes → **replay attack** → 401 + invalidate entire session
4. If no → issue new tokens, blacklist old hash

```java
// Redis keys
token:<hash>                    → TTL per policy, marks refresh token as used
session:<sessionId>:valid        → TTL per policy, marks session as active
session:<sessionId>:connections → Set of active connection IDs
```

## Step-up auth for cross-connection operations

Payment, profile change, and other sensitive actions require step-up:
1. User initiates sensitive action
2. Backend returns `401 step_up_required`
3. App prompts for OTP (re-auth)
4. Server issues new access token with `amr: [pwd, mfa]` and `sid` (same session)
5. Action proceeds with elevated token

Step-up tokens are scoped: they carry the `step_up` scope and the action
they authorize (e.g. `payment:initiate`).

## JWKS endpoint

Public keys served at `GET /.well-known/jwks.json`:

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "2026-09-03-1",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

Keys rotate every 90 days. Old keys remain valid for 7 days after rotation
(clock skew + token expiry grace period).

## Refresh token family (rotation tracking)

To detect unauthorized refresh token theft (e.g. man-in-the-middle):
- Each refresh token tracks `rotatedFrom` (previous token hash)
- A token can only be used once (rotation)
- If a token is reused → full session revocation

## Consequences

### Positive
- No shared session state → horizontal scaling
- Replay detection catches stolen refresh tokens
- Step-up enables sensitive cross-connection ops
- Redis blacklist is fast and tenant-isolated
- Per-tenant TTL matches the business request while keeping the default
  defensible
- All TTL changes are auditable in MongoDB, not hidden in env files

### Negative
- Redis dependency for blacklist (mitigated by cluster mode)
- Token revocation is eventual (max 24h for default access tokens,
  42d for opt-in long-lived tenants)
- Mobile apps must implement token refresh logic
- Tenants that opt in to long TTL must enable device binding — a
  misconfigured tenant gets a hard failure on first issuance from a new
  device, not silent policy violation

## References

- `Planning doc/07_security/01_Security_Privacy_Compliance.md` (token policy)
- `Planning doc/09_roadmap_migration/03_ADR_Key_Decisions.md` (ADR-011 was OPEN)
- `backend/customer-identity-service/.../security/JwtIssuer.java` (per-tenant TTL loader)
- `backend/customer-identity-service/.../service/CustomerSessionService.java`
- `backend/customer-identity-service/.../service/TenantTokenPolicyService.java`
- `backend/customer-identity-service/src/main/resources/db/migration/V4__tenant_token_policy.sql`

## Token architecture

```
Access Token (JWT, RS256 signed)
├── iss: selfcare-platform
├── sub: connectionId
├── aud: tenantId
├── tenantId: dialog-lk
├── scope: [balance:read, bills:read]
├── primaryConnectionId: CONN-001
├── linkedConnectionIds: [CONN-002, CONN-003]
├── amr: [pwd]           // Authentication methods
├── sid: <session-id>    // Session ID for step-up
└── exp: +15min

Refresh Token (opaque, stored in Redis)
├── tokenHash: sha256(refresh-token)
├── connectionId: CONN-001
├── sessionId: <uuid>
├── issuedAt: 2026-09-03T10:00:00Z
├── expiresAt: 2026-09-04T10:00:00Z
└── rotatedFrom: null | <previous-token-hash>
```

## Lifecycle

1. **Login** — OTP or password verified → issue access token (15min) + refresh token (24h)
2. **Use** — Mobile app sends access token in `Authorization: Bearer <token>`
3. **Refresh** — Before expiry, app calls `POST /auth/refresh` with refresh token
4. **Rotate** — New refresh token issued, old one blacklisted (replay detection)
5. **Logout** — Both tokens blacklisted

## Replay detection

Every refresh token has a `tokenHash` in Redis. When refresh is called:
1. Hash incoming token
2. Check Redis: `token:<hash>` exists?
3. If yes → **replay attack** → 401 + invalidate entire session
4. If no → issue new tokens, blacklist old hash

```java
// Redis keys
token:<hash>                    → TTL 24h, marks refresh token as used
session:<sessionId>:valid        → TTL 24h, marks session as active
session:<sessionId>:connections → Set of active connection IDs
```

## Step-up auth for cross-connection operations

Payment, profile change, and other sensitive actions require step-up:
1. User initiates sensitive action
2. Backend returns `401 step_up_required`
3. App prompts for OTP (re-auth)
4. Server issues new access token with `amr: [pwd, mfa]` and `sid` (same session)
5. Action proceeds with elevated token

Step-up tokens are scoped: they carry the `step_up` scope and the action
they authorize (e.g. `payment:initiate`).

## JWKS endpoint

Public keys served at `GET /.well-known/jwks.json`:

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "2026-09-03-1",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

Keys rotate every 90 days. Old keys remain valid for 7 days after rotation
(clock skew + token expiry grace period).

## Refresh token family (rotation tracking)

To detect unauthorized refresh token theft (e.g. man-in-the-middle):
- Each refresh token tracks `rotatedFrom` (previous token hash)
- A token can only be used once (rotation)
- If a token is reused → full session revocation

## Consequences

### Positive
- No shared session state → horizontal scaling
- Replay detection catches stolen refresh tokens
- Step-up enables sensitive cross-connection ops
- Redis blacklist is fast and tenant-isolated

### Negative
- Redis dependency for blacklist (mitigated by cluster mode)
- Token revocation is eventual (max 15min for access tokens)
- Mobile apps must implement token refresh logic
