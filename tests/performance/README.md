# OMOBIO Selfcare Platform — k6 Performance Test Suite

This directory contains k6 performance test scripts that validate the platform's
non-functional requirements (NFRs).

## Requirements

- [k6](https://k6.io/) v0.49+ installed
- A test environment (dev/stg) running the platform
- A valid API_GATEWAY URL

## Running

```bash
# 1. Dashboard BFF fan-out (NFR-PERF-003 — 300ms P95)
k6 run tests/performance/01_dashboard_load.js

# 2. Payment idempotency under load
k6 run tests/performance/02_payment_idempotency.js

# 3. Config service compile/publish (NFR-PERF-004 — 100ms P95 cached)
k6 run tests/performance/03_config_compile_publish.js

# 4. Resilience — provider outage
k6 run tests/performance/04_resilience_provider_outage.js

# 5. Auth/OTP throughput
k6 run tests/performance/05_auth_otp_throughput.js

# Custom target
API_GATEWAY=https://api.stg.omobio.io \
TENANT_ID=dialog-lk \
k6 run --vus 100 --duration 5m tests/performance/01_dashboard_load.js
```

## NFR Coverage

| Test | NFR | Threshold | Scenario |
|---|---|---|---|
| `01_dashboard_load.js` | NFR-PERF-003 | P95 < 300ms | load (100-200 VUs) |
| `01_dashboard_load.js` | NFR-AVL-002 | partial response rate > 90% | all |
| `02_payment_idempotency.js` | NFR-AVL-004 | double-charge < 0.1% | concurrent same key |
| `03_config_compile_publish.js` | NFR-PERF-001, NFR-PERF-004 | P95 < 100ms cached | 1000 VUs |
| `04_resilience_provider_outage.js` | NFR-AVL-002 | dashboard success > 99% | provider down |
| `05_auth_otp_throughput.js` | NFR-SEC | rate limit applies | 200 VUs |

## Output

- `results/` — JSON output (`--out json=results/X.json`)
- HTML reports via `k6-reporter`:
  ```bash
  K6_WEB_DASHBOARD=true k6 run tests/performance/01_dashboard_load.js
  ```

## CI Integration

The k6 tests run in the `ci/github-actions/performance.yml` workflow on:
- Pull requests that touch `backend/` or `config-tenant-service/`
- Nightly at 02:00 UTC (against stg)

Performance regressions (>10% P95 increase) fail the build.

## Adding New Tests

1. Follow the existing structure (options, thresholds, custom metrics)
2. Document which NFR you validate in a header comment
3. Add the script to the CI workflow `k6-performance` job
4. Update this README with the new script + NFR
