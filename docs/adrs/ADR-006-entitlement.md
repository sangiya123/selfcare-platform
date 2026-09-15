# ADR-006: Dialog Primary-Number Linked-List Entitlement

**Status**: ACCEPTED
**Date**: 2026-09-03
**Deciders**: selfcare Architecture Council, Dialog Architecture Team

## Context

Dialog's selfcare product serves customers who have one **primary MSISDN**
(the number they log in with) and may have several **linked connections**
(other Dialog numbers on the same account — family, business, etc.).

When a user requests an action (e.g., pay a bill for connection B, recharge
connection C, view usage for connection D), the system must verify they're
authorized for that target connection.

Two models were considered:

### Model A: NIC-based ownership
Multiple connections are considered the same customer's if they're all
registered under the same National Identity Card (NIC) number.

**Problem**: This doesn't match Dialog's business reality. The NIC field on
a connection is unreliable (foreigners, businesses, joint accounts). A
customer's "linked connections" are defined by Dialog's profile system, not
by their NIC.

### Model B: Primary-number linked list (PROPOSED)
The logged-in primary MSISDN has an explicit `linkedConnectionIds[]` list
fed by Dialog's profile system via Kafka. A target connection is authorized
if (and only if) it's in the current session's primary number's linked list.

**Adopted by Dialog's existing selfcare backend.**

## Decision

**Model B: Primary-number linked list entitlement.**

- Session always carries `primaryConnectionId` (the primary MSISDN the user logged in with)
- Each connection has an explicit `accountId`
- Each account has a `linkedConnectionIds[]` list (Database of Record: Dialog Profile System)
- Kafka event `account.connection.changed` updates the cached linked-list
- Authorization check: `targetConnectionId in primaryConnection.account.linkedConnectionIds`

## Why this matters

- **Authorization is explicit**, not inferred — clear audit trail
- **Matches the business model** — Dialog's profile system is the source of truth
- **Fast at request time** — Redis caches the linked list per account
- **Safe under cache miss** — DB lookup fallback is always available

## Implementation

```java
// Authorization check
public void authorizeConnectionAction(String accountId, String targetConnectionId, String action) {
    // 1. Check Redis cache
    Boolean isMember = redisTemplate.opsForSet().isMember(
        "selfcare:linked:" + accountId, targetConnectionId);
    if (isMember) return;

    // 2. Fallback to DB
    Account account = accountRepository.findById(accountId).orElseThrow();
    Connection target = connectionRepository.findById(targetConnectionId).orElseThrow();
    if (!target.getAccountId().equals(accountId)) {
        throw new ForbiddenException(action, targetConnectionId);
    }

    // 3. Cache for next time
    redisTemplate.opsForSet().add("selfcare:linked:" + accountId, targetConnectionId);
}
```

## Kafka feed

```yaml
topic: account.connection.changed
payload:
  tenantId: dialog-lk
  accountId: ACC-001
  linkedConnectionIds: [CONN-001, CONN-002, CONN-003]
  relationshipVersion: 17
  changedAt: 2026-09-03T15:42:00Z
```

On receipt, the entitlement service invalidates the Redis cache for the
account and re-populates on next read.

## Cross-connection operations

Operations like "pay bill for connection B from connection A's payment method"
require:
1. **Action authorization** — connection B is in the linked list
2. **Payment authorization** — connection A has payment method available
3. **Step-up** — above a threshold, requires re-auth

## What "linked" means in insurance

For insurance tenants, the analogous concept is `policyBeneficiaries[]`:
the policyholder's "linked policies" are the ones they're the named
policyholder or an authorized beneficiary on. AIA provides this via their
customer API; we cache it the same way.

## Cross-industry rule

The kernel is industry-neutral. The entitlement check is implemented as
`EntitlementService.authorize(actor, target, action)` and dispatches to the
industry pack's specific implementation (linked-connection for telco,
policy-relationship for insurance, etc.).
