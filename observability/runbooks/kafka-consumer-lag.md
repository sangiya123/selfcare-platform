# Kafka Consumer Lag — Incident Runbook

**Severity**: SEV-2 (growing lag) / SEV-3 (stable lag)  
**Service**: Any Kafka consumer service  
**Detection**: Grafana `kafka-consumer-lag.json`, Prometheus `kafka_consumer_lag` metric

---

## Symptoms

- Lag growing on one or more consumer groups
- Stale data in downstream services (dashboard shows old data)
- Kafka consumer group status: `LAG > 0` for extended period
- Downstream effects: reports stale, notifications delayed, payments stuck

---

## Diagnosis

### 1. Identify consumer group and topic

```bash
# List all consumer groups
kubectl exec -it deploy/kafka -n kafka -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Check lag for a specific group
kubectl exec -it deploy/kafka -n kafka -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group omobio-audit-consumer --describe
```

### 2. Check which partition(s) have lag

```
GROUP, TOPIC, PARTITION, CURRENT-OFFSET, LOG-END-OFFSET, LAG, CONSUMER-HOST
omobio-audit-consumer, audit.events, 0, 12345, 12900, 555, kafka-consumer-0.omobio
```

### 3. Identify the slow consumer

- Is the consumer pod running? `kubectl get pods -n omobio-prod | grep audit`
- Check consumer logs for errors: `kubectl logs -f deploy/audit-service --tail=100`
- Common errors: OOM, crash loop, DB connection exhaustion

### 4. Check consumer throughput

```bash
# Messages per second consumed
kubectl exec -it deploy/audit-service -n omobio-prod -- \
  curl localhost:8085/actuator/metrics/kafka_consumer_records_consumed_total
```

---

## Mitigation

### If consumer is down / crash-looping

```bash
# Restart the consumer
kubectl rollout restart deployment/audit-service -n omobio-prod
kubectl rollout status deployment/audit-service -n omobio-prod

# If OOM: temporarily increase memory limit
kubectl patch deployment audit-service -n omobio-prod \
  -p '{"spec":{"template":{"spec":{"containers":[{"name":"audit-service","resources":{"limits":{"memory":"2Gi"}}}]}}}}'
```

### If lag is from slow processing (not crashed)

```bash
# Scale up consumer replicas (if consumer group supports multiple instances)
kubectl scale deployment audit-service -n omobio-prod --replicas=4

# Or: reset consumer offset to latest (loses messages — only in dev/stg)
kubectl exec -it deploy/kafka -n kafka -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group omobio-audit-consumer \
  --topic audit.events \
  --reset-offsets --to-latest --execute
```

### If lag is from producer burst

- Lag will self-recover once burst subsides
- Monitor for 10 minutes before escalating
- Check producer rate: `kubectl exec deploy/kafka -- kafka-producer-perf-test.sh ...`

---

## Prevention

| Action | How |
|---|---|
| Lag alerting | Prometheus alert: `kafka_consumer_lag > 10000` for > 5 min |
| Consumer autoscaling | KEDA Kafka scaler based on lag metric |
| Dead letter queue | Implement DLQ for poison messages causing consumer hang |
| Consumer health | `kafka-consumer-lag.json` Grafana dashboard |

---

## Key Topics and Their Expected Lag SLA

| Topic | Consumer | Max Lag | SLA |
|---|---|---|---|
| `account.events` | account-entitlement-service | < 1,000 | 30 sec |
| `payment.events` | payment-service | < 500 | 10 sec |
| `audit.events` | audit-service | < 10,000 | 5 min |
| `notification.events` | notification-service | < 5,000 | 2 min |

---

## Escalation

If lag does not reduce within 30 minutes of mitigation → escalate to SEV-2 and engage the owning team.  
If lag > 100,000 and growing → SEV-1, consider resetting offset after business hours review.
