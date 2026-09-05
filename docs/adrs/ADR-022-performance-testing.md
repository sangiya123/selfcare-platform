# ADR-022: k6 Performance Testing Strategy

## Status
Accepted — 2026-09-04

## Context
The platform has strict non-functional requirements for latency,
throughput, and availability. The Test Strategy spec requires:

- Load test for dashboard BFF (25 providers fan-out, 200ms partial response)
- Idempotency / no-double-charge for payments
- Config compile/publish latency (sub-100ms after first read)
- Resilience: dashboard success rate during provider outage
- Auth: OTP throughput under load

k6 was chosen for:
- JS test scripts (easy to read, broad skill base)
- Native `ramping-vus` and `constant-vus` executors
- Custom Trend / Rate / Counter metrics
- Built-in thresholds for pass/fail
- Single static binary, container-friendly

## Decision
We adopt a **k6 + GitHub Actions** performance testing pipeline:

### 1. Test layout
```
tests/performance/
├── 01_dashboard_load.js          # 100-200 VUs, P95 < 300ms
├── 02_payment_idempotency.js     # 50 VUs, no double charge
├── 03_config_compile_publish.js  # 1000 VUs, P95 < 100ms
├── 04_resilience_provider_outage.js  # Inject outage, success > 99%
├── 05_auth_otp_throughput.js     # 200 VUs, OTP rate limit
├── 06_search_fulltext.js         # 100 VUs (future)
├── 07_notification_burst.js      # 500 VUs push notifications (future)
├── README.md
└── results/                      # Archived JSON summaries
```

### 2. Environments
- `stg` (mandatory): run full suite nightly + on PR to main
- `pr-preview` (optional): lightweight smoke on every PR
- `prod` (optional): canary test before blue/green cutover

### 3. CI integration
- `ci/github-actions/performance.yml` triggers on:
  - PR to `main` touching `backend/`
  - Nightly schedule (02:00 UTC)
  - Manual `workflow_dispatch`
- Jobs: k6-smoke, k6-load, k6-resilience, perf-regression-check
- `scripts/perf-compare.py` compares current run vs baseline

### 4. Custom metrics
- `dashboard_latency` — p(95) per widget call
- `widget_success_rate` — successful widget responses / total
- `partial_response_rate` — dashboards with ≥80% widgets
- `double_charge_rate` — must stay < 0.001
- `config_publish_latency` — p(95) for config publish
- `provider_call_total` (counter) — per-provider
- `ai_call_latency` (trend) — p(95) for AI gateway
- `feature_flag_eval_latency` — p(95) for flag eval

### 5. Pass/fail thresholds
- Encoded directly in each script (k6 native thresholds)
- ALSO checked by `perf-compare.py` for NFR hard limits
- Either source of failure blocks merge

### 6. Baseline management
- After every successful release, `main` results are promoted to `baseline/`
- Stored in repo as `tests/performance/baselines/{date}.json`
- Rollback baseline available via `git checkout` of last good commit

### 7. Reporting
- GitHub Actions step `performance-bot` posts summary comment on PR
- Grafana dashboard `k6-runs` ingests JSON via Loki
- SRE alert if NFR threshold breached in `stg`

## Implementation
- Docker image: `grafana/k6:0.50.0` (pinned)
- Run via `k6 run --out json=results/run.json script.js`
- Post-process to summary with `k6-to-summary` or in-script `handleSummary()`

## Consequences

Positive:
- Catch regressions before merge
- Documented NFR compliance evidence
- Repeatable, automated, no manual test rig

Negative:
- k6 results can be flaky (run 3 times, take median)
- Maintain baselines as code
- Some scenarios (provider outage) require staging fault-injection

## Compliance
- Test Strategy §8: Performance layer
- NFR-PERF-001/003/004
- NFR-AVL-002/003/004
