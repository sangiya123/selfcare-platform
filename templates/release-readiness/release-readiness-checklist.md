# Release Readiness Checklist — <Release name / version>

| Field | Value |
|---|---|
| **Release ID** | REL-NNNN |
| **Version** | semver (e.g. `2026.09.1`) |
| **Target environments** | dev / stg / reg / prod |
| **Release manager** | name + email |
| **Release date** | YYYY-MM-DD |
| **Rollback deadline** | YYYY-MM-DD (typically 7 days post-release) |

## Code Readiness

- [ ] All PRs merged to release branch
- [ ] Branch is up-to-date with `main`
- [ ] No `WIP` or `DO NOT MERGE` markers
- [ ] All `TODO` comments addressed or backlogged
- [ ] No commented-out code in production paths

## Test Readiness

### Unit tests
- [ ] All services pass unit tests (`mvn test`)
- [ ] Coverage ≥ 60% line coverage
- [ ] Coverage ≥ 80% on changed code

### Integration / conformance
- [ ] All conformance tests pass (`tests/conformance`)
- [ ] API contract tests pass
- [ ] Tenant isolation tests pass
- [ ] Auth flow + refresh-token-replay tests pass
- [ ] Dashboard partial-response tests pass
- [ ] Payment idempotency tests pass
- [ ] Provider adapter tests pass (all 19 canonical interfaces)
- [ ] AI tool-permission + RAG isolation tests pass
- [ ] Journey simulation tests pass

### End-to-end
- [ ] Mobile E2E (Detox) passes on iOS + Android
- [ ] Admin E2E (Playwright) passes
- [ ] Synthetic monitoring probes pass
- [ ] ZAP DAST scan: no HIGH/CRITICAL findings

### Performance
- [ ] k6 load test: p99 < 1.5s, error rate < 0.1% at 2x peak
- [ ] Memory leak check: 24h soak, no growth
- [ ] Capacity test: documented per-environment max RPS

### Security
- [ ] SonarQube quality gate passed
- [ ] Checkstyle, SpotBugs, PMD: no critical findings
- [ ] OWASP dependency-check: no CVSS ≥ 7
- [ ] Trivy filesystem + image scan: no HIGH/CRITICAL
- [ ] Gitleaks secret scan: clean
- [ ] Semgrep SAST: no critical findings
- [ ] ZAP DAST: no HIGH/CRITICAL
- [ ] Authorization negative tests pass
- [ ] AI red-team evaluation: pass rate ≥ 95%

## Documentation Readiness

- [ ] CHANGELOG.md updated
- [ ] API reference regenerated (OpenAPI)
- [ ] ADRs updated or created
- [ ] Runbooks updated
- [ ] Release notes drafted
- [ ] Customer-facing release notes drafted (if applicable)

## Operational Readiness

- [ ] Helm values updated for target env
- [ ] ArgoCD ApplicationSet updated
- [ ] Database migrations reviewed + tested in dev/stg
- [ ] Feature flags configured (defaults OFF if risk is high)
- [ ] Dashboards created / updated in Grafana
- [ ] Alerts configured (error rate, latency, saturation)
- [ ] On-call rotation confirmed for release window
- [ ] Rollback procedure documented + tested
- [ ] Communication sent to stakeholders (CS, Sales, Operators)

## Compliance Readiness

- [ ] GDPR impact assessed (consent, PII, retention)
- [ ] PCI-DSS scope reviewed (if touching payment)
- [ ] Data residency requirements met
- [ ] Audit logging verified
- [ ] License compliance: no GPL/AGPL/LGPL in dependencies

## Approval

| Role | Name | Date | Approved |
|---|---|---|---|
| Engineering lead | | | ☐ |
| QA lead | | | ☐ |
| Security | | | ☐ |
| SRE | | | ☐ |
| Product owner | | | ☐ |
| Tenant sponsor (if cross-tenant) | | | ☐ |

## Release Notes

### What's new

- …

### What's changed

- …

### What's deprecated

- …

### Migration / upgrade

- …

### Known issues

- …

## Post-Release

- [ ] Smoke test in production (10 min after release)
- [ ] SLO check at 1h, 4h, 24h
- [ ] Customer feedback review at 7 days
- [ ] Postmortem scheduled (if any incident during release)
