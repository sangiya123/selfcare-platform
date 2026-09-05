# Runbook: Payment Timeout or Stuck Transaction

## Overview
This runbook covers scenarios where payment transactions are stuck in PENDING
state, time out, or where the upstream payment provider is not responding.

**WARNING: Do NOT blindly retry debits.** A timeout does not mean the
transaction failed. The customer may have been charged. Always reconcile
first.

## Symptoms
- PaymentStuckTransactions alert (> 100 PENDING for 15m)
- Users report "payment failed but money deducted"
- Provider callbacks not arriving
- Idempotency conflict rate high
- High latency on /api/v1/payments/*

## Quick checks
1. **Check current PENDING transactions**
   ```bash
   kubectl -n omobio-prod exec -it mysql-0 -- mysql -e \
     "SELECT id, tenant_id, amount, status, created_at FROM payment_transaction
      WHERE status='PENDING' AND created_at < NOW() - INTERVAL 5 MINUTE
      ORDER BY created_at LIMIT 50"
   ```
2. **Check payment provider health**
   ```bash
   curl -s -m 5 https://api.dialog.lk/payments/healthcheck | jq .
   ```
3. **Check callback webhook status**
   ```bash
   kubectl -n omobio-prod logs -l app=payment-service --tail=200 | grep -E "callback|webhook"
   ```
4. **Check reconciliation lag**
   ```bash
   kubectl exec -it prometheus-0 -- promtool query instant \
     'time() - payment_reconciliation_last_run_timestamp_seconds'
   ```

## Common causes

### A. Provider timeout — customer charged but no callback
**Symptom**: Money deducted from customer account, but transaction is still PENDING
**Fix**: Run reconciliation; do NOT retry
```bash
# Manual reconciliation for stuck transaction
curl -X POST http://api-gateway/api/v1/admin/payments/reconcile \
  -H "X-Tenant-Id: <tenant>" \
  -H "Content-Type: application/json" \
  -d '{"transactionId": "<txn-id>"}'

# Reconciliation queries provider for actual status, then updates local record
```

### B. Webhook callback endpoint unreachable
**Symptom**: All callbacks failing, transactions stuck PENDING
**Fix**: Verify webhook URL and routing
```bash
# Check ingress
kubectl -n omobio-prod get ingress payment-callback
curl -v -X POST http://api-gateway/api/v1/payments/callback
```

### C. Step-up auth required but not completed
**Symptom**: High-value payment transactions stuck
**Fix**: Verify step-up is properly required and the user is presented with the OTP step
```bash
# Check step-up rate
kubectl exec -it prometheus-0 -- promtool query instant \
  'sum(rate(step_up_required_total[5m])) by (use_case)'
```

### D. Idempotency key reused with different payload
**Symptom**: PaymentIdempotencyConflictRateHigh alert
**Fix**: Client bug — investigate client SDK
```bash
# Check conflict pattern
kubectl -n omobio-prod logs -l app=payment-service | \
  grep "Idempotency conflict" | tail -20
```

### E. Provider circuit breaker open
**Symptom**: All provider calls returning 503
**Fix**: Verify circuit breaker status; fall back to alternative provider
```bash
kubectl exec -it payment-service-0 -- \
  curl -s http://localhost:8080/actuator/circuitbreakers | jq .

# If circuit is open — wait for cooldown, or force-close
```

## Reconciliation (the safe path)

The payment-service reconciliation job runs every 30 minutes. It:
1. Queries the provider for all transactions in PENDING state > 5 min old
2. Updates local records to match provider truth
3. Marks duplicates as DUPLICATE
4. Refunds duplicates via reverse-transaction

**Do NOT manually issue refunds without consulting Finance.**

```bash
# Trigger ad-hoc reconciliation
kubectl -n omobio-prod exec -it payment-service-0 -- \
  java -jar /app/app.jar --reconcile --tenant=<tenant> --older-than=PT10M
```

## Verification
After reconciliation:
```bash
# Verify PENDING count drops
kubectl -n omobio-prod exec -it mysql-0 -- mysql -e \
  "SELECT COUNT(*) FROM payment_transaction
   WHERE status='PENDING' AND created_at < NOW() - INTERVAL 10 MINUTE"
```

## Post-incident
1. File incident report including the count of affected customers
2. For each affected customer, verify the final transaction state matches their statement
3. Send apology notification if there was significant user impact
4. Add a regression test for the specific failure mode

## Escalation
If the issue involves:
- Customer money deducted without service delivery → page Finance + Customer Care
- Provider API compromised → page Security immediately
- Bulk duplicate debits → page C-suite per incident response plan
