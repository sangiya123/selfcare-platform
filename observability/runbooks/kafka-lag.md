# Runbook: Kafka Consumer Lag

## Overview
This runbook covers scenarios where Kafka consumer groups fall behind, causing
stale read models, late notifications, or missed domain events.

## Symptoms
- KafkaConsumerLagHigh alert (> 10k messages behind for 10m)
- KafkaConsumerLagCritical alert (> 100k messages behind for 5m)
- Profile read model shows old data
- Notifications delivered late
- Compact entitlement cache shows stale connections

## Quick checks
1. **Which consumer groups are lagging?**
   ```bash
   kubectl exec -it kafka-0 -- kafka-consumer-groups \
     --bootstrap-server kafka:9092 \
     --list | grep selfcare
   ```
2. **What's the lag?**
   ```bash
   kubectl exec -it kafka-0 -- kafka-consumer-groups \
     --bootstrap-server kafka:9092 \
     --describe --group selfcare-account-entitlement-consumer
   ```
3. **Are consumers running?**
   ```bash
   kubectl -n selfcare-prod get pods -l app=account-entitlement-service
   ```
4. **Check broker health**
   ```bash
   kubectl exec -it kafka-0 -- kafka-broker-api-versions \
     --bootstrap-server kafka:9092
   ```

## Common causes

### A. Consumer pod crashed or is OOM-killed
**Symptom**: Pod restart count increasing, lag is increasing
**Fix**: Restart consumer and scale up
```bash
kubectl -n selfcare-prod rollout restart deploy/account-entitlement-service
kubectl -n selfcare-prod scale deploy/account-entitlement-service --replicas=5
```

### B. Downstream sink (database/Redis) is slow
**Symptom**: Consumer is alive but slow, batch latency high
**Fix**: Check downstream metrics; pause consumption if needed
```bash
# Check Redis latency
redis-cli --latency -h redis-auth

# Check MySQL slow query log
kubectl -n selfcare-prod exec -it mysql-0 -- \
  mysql -e "SHOW FULL PROCESSLIST"
```

### C. Partition rebalance storm
**Symptom**: Frequent rebalance events in logs, lag spikes
**Fix**: Increase session timeout, scale consumers
```bash
# Increase session timeout
kubectl -n selfcare-prod set env deploy/account-entitlement-service \
  SPRING_KAFKA_CONSUMER_PROPERTIES_SESSION_TIMEOUT_MS=45000
```

### D. Idempotent replay needed
**Symptom**: After a backlog clears, downstream shows duplicate effects
**Fix**: Verify consumer uses idempotency keys and is replay-safe
```bash
# Inspect consumer code for idempotency
grep -r "isIdempotent\|deduplicationKey" \
  /app/src/main/java/com/selfcare/account/
```

### E. Topic is undersized (too few partitions)
**Symptom**: All consumers in group, but lag still grows
**Fix**: Add partitions
```bash
kubectl exec -it kafka-0 -- kafka-topics \
  --bootstrap-server kafka:9092 \
  --alter --topic selfcare.profile.events \
  --partitions 12
```

## Verification
After mitigation, verify the lag is draining:
```bash
watch -n 5 "kubectl exec -it kafka-0 -- kafka-consumer-groups \
  --bootstrap-server kafka:9092 \
  --describe --group selfcare-account-entitlement-consumer"
```

## Post-incident
1. Run a profile reconciliation job to ensure read model matches source-of-truth
2. Review downstream SLA — was the consumer waiting on a slow database call?
3. Update partition count and consumer replica count for the affected topic
4. If lag > 1M, trigger a one-off backfill job

## Escalation
Page the Data Platform team if lag > 100k for any consumer group for > 30 min.
