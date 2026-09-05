# Feature Definition — <Feature name>

| Field | Value |
|---|---|
| **Feature ID** | F-NNNN |
| **Business owner** | name + email |
| **Engineering lead** | name + email |
| **Design lead** | name + email |
| **Target release** | YYYY-Qx |
| **Status** | DRAFT / APPROVED / IN_PROGRESS / SHIPPED / CANCELLED |

## Problem

What user problem are we solving? What is the desired outcome? Cite
user research, support tickets, operator requests, or business KPIs.

> Example: "Customers switching from Dialog to Hutch in the same
> selfcare app lose their saved layout. They must re-personalise after
> every tenant switch."

## Outcome

What does success look like? Be specific and measurable.

> Example: "Reduce layout re-personalisation time from ~2 minutes to
> < 5 seconds for 95% of cross-tenant switches. Target completion by
> end of Q3."

## Eligible Users

- **Industries:** TELCO, INSURANCE, TRAVEL, BANKING, …
- **Tenants / clients:** e.g. dialog-lk, hutch-lk, airtel-lk, aia-sg
- **LOBs / customer types / segments:** MOBILE, DTV, FIBRE; consumer, SMB
- **App versions / platforms:** iOS ≥ 1.4.0, Android ≥ 1.4.0, web ≥ 2.0
- **Geographic scope:** LK, SG, multi-country

## User Stories

- As a <role>, I want <capability>, so that <benefit>.
- …

## Solution Sketch

High-level design (1-2 paragraphs). Reference design docs and PRs.

## Affected Surfaces

- **Mobile components / pages:** …
- **Studio features:** …
- **Canonical APIs:** …
- **Provider interfaces:** …
- **Data sources:** …
- **Kafka topics:** …
- **Auth / authorization:** …

## Authorization Policy

| Action | Actor | Target | Policy |
|---|---|---|---|
| View own bill | Customer | self.connection | ALLOW |
| View another customer's bill | Customer | other.connection | DENY (linked-list rule) |
| Pay another customer's bill | Customer | other.connection | STEP_UP + DENY unless linked |

## Acceptance Criteria

- [ ] User story 1 satisfied
- [ ] User story 2 satisfied
- [ ] Coverage ≥ 80% on new code
- [ ] No P1/P2 lint findings
- [ ] No CVSS ≥ 7 security findings
- [ ] No accessibility regressions (axe-core)
- [ ] p99 latency < X ms
- [ ] Sentry breadcrumbs capture relevant context

## Out of Scope

- …

## Dependencies

- **Upstream:** …
- **Downstream:** …
- **Cross-team:** …

## Rollout

- **Feature flag:** `omobio.feature.<feature-name>` (default OFF)
- **Canary cohort:** internal users → 5% → 25% → 100%
- **Rollback:** set flag to OFF
- **Telemetry:** events to track, dashboards to add

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| … | L/M/H | L/M/H | … |
