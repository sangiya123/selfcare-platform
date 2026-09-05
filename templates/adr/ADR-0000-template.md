# ADR-NNNN: <Short, descriptive title>

| Field | Value |
|---|---|
| **ADR ID** | ADR-NNNN |
| **Status** | DRAFT / PROPOSED / ACCEPTED / SUPERSEDED / DEPRECATED |
| **Date** | YYYY-MM-DD |
| **Deciders** | Names of decision-makers |
| **Reviewers** | Names of required reviewers |
| **Consulted** | Names of SMEs consulted |
| **Informed** | Names of those who should be informed |

## Context

What is the issue or opportunity we're seeing? What are the forces at play
(constraints, requirements, drivers)? Reference relevant planning docs,
previous ADRs, RFCs, and incident reports. State *why* this decision
needs to be made now.

## Decision

What did we decide? Be specific. State the decision in the active voice.
This section should be self-contained — a reader should be able to read
just this section and understand what was chosen.

> Example: "We will use PostgreSQL 16 with the pg_stat_statements extension
> for query performance monitoring. Read replicas will be deployed in
> each region."

## Alternatives Considered

What other options were on the table? For each, briefly describe and
explain why it was rejected.

### Alternative 1: <Name>

- **Pros:** …
- **Cons:** …
- **Why rejected:** …

### Alternative 2: <Name>

- **Pros:** …
- **Cons:** …
- **Why rejected:** …

## Consequences

What becomes easier or harder as a result of this decision?

### Positive

- …
- …

### Negative

- …
- …

### Neutral

- …

## Security & Operational Impact

- **Security impact:** What new attack surface, mitigations, or compliance
  obligations does this introduce?
- **Operational impact:** What new dashboards, alerts, runbooks, on-call
  rotations, capacity planning, or maintenance tasks?
- **Data impact:** What data classification, retention, residency, or
  lineage changes?
- **Cost impact:** Expected one-time and recurring cost changes.

## Migration / Rollout Plan

How do we get from the current state to the new state?

- **Phase 1:** …
- **Phase 2:** …
- **Rollback plan:** …
- **Feature flag / kill switch:** …

## Validation

How will we know this decision was correct?

- **Metrics to watch:** …
- **SLOs affected:** …
- **Review date:** YYYY-MM-DD (typically 6–12 months after acceptance)

## References

- Planning doc: …
- Previous ADRs: …
- External references: …
- Related issues / PRs: …
