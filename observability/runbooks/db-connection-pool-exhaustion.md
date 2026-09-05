# DB Connection Pool Exhaustion — Incident Runbook

**Severity**: SEV-2  
**Service**: Any Spring Boot service with MySQL/MongoDB  
**Detection**: HikariCP metrics, Pod OOM, `too many connections` errors

---

## Symptoms

- HTTP 503 or connection timeout errors on one or more services
- Slow DB queries or hanging connections
- `Cannot acquire connection from pool` in service logs
- MySQL: `ERROR 1040 (HY000): Too many connections`
- HikariCP `active=poolSize, idle=0, waiting=N` (N growing)

---

## Diagnosis

### 1. Identify affected service

```bash
# Check which pods are restarting or have OOM
kubectl -n omobio-prod get pods | grep -v Running | grep -v Completed

# Check DB connection metrics per service
kubectl -n omobio-prod exec -it deploy/api-gateway -- curl localhost:8080/actuator/metrics/hikaricp.connections.active
kubectl -n omobio-prod exec -it deploy/api-gateway -- curl localhost:8080/actuator/metrics/hikaricp.connections
```

### 2. Check MySQL max connections

```bash
kubectl exec -it deploy/mysql -n database -- mysql -u root -p \
  -e "SHOW STATUS LIKE 'Threads_connected';"
kubectl exec -it deploy/mysql -n database -- mysql -u root -p \
  -e "SHOW PROCESSLIST;" | wc -l
```

### 3. Identify the culprit query

```sql
-- In MySQL shell
SELECT id, user, host, db, command, time, state, info
FROM information_schema.processlist
WHERE command != 'Sleep'
ORDER BY time DESC
LIMIT 20;
```

### 4. Check HikariCP pool config

```bash
kubectl exec -it deploy/admin-identity-service -n omobio-prod -- \
  curl localhost:8082/actuator/configprops | jq '.spring.datasource.hikari'
```

---

## Mitigation

### Immediate (scale up pool or connections)

```bash
# Temporarily increase max_connections on MySQL
kubectl exec -it deploy/mysql -n database -- mysql -u root -p \
  -e "SET GLOBAL max_connections = 500;"

# Restart affected pod to clear stale connections
kubectl rollout restart deployment/<affected-service> -n omobio-prod
```

### Short-term (kill long-running queries)

```sql
-- Kill queries running longer than 60 seconds
SELECT CONCAT('KILL ', id, ';')
FROM information_schema.processlist
WHERE command != 'Event' AND time > 60;
```

### Long-term fix

1. Identify slow/long-running queries — add to slow query log
2. Add index to missing columns
3. Tune HikariCP pool size: `core = (num_cores * 2) + effective_spindle_count`
4. Set `validationTimeout` and `connectionTimeout` appropriately
5. Ensure connection pool monitoring is in Grafana (`db-connection-pools.json`)

---

## Prevention

| Action | How |
|---|---|
| Connection pool monitoring | Grafana `db-connection-pools.json` dashboard |
| Slow query alerting | Prometheus alert on `hikaricp_connections_active > 80% of pool` |
| Connection leak detection | HikariCP `leakDetectionThreshold=30s` in dev/stg |
| Query timeout | MySQL `max_execution_time` or application-level timeout |

---

## Escalation

If `max_connections` has been raised more than twice in a week → escalate to SEV-1 and schedule DB capacity review.
