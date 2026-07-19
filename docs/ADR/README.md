# Architecture Decision Records (ADR)

This folder holds one file per significant, hard-to-reverse technical decision (e.g., "use PostgreSQL over MongoDB," "JWT over server sessions," "monolith over microservices for v1"). No ADRs exist yet — none have been made, since design work has not started (see [../01-SRS.md](../01-SRS.md), Constraint C1).

## Naming
`ADR-001.md`, `ADR-002.md`, … — sequential, never renumbered or reused even if an ADR is later superseded.

## Template for each ADR

```markdown
# ADR-XXX: <short decision title>

**Status:** Proposed | Accepted | Superseded by ADR-YYY
**Date:** YYYY-MM-DD

## Context
What problem or question forced this decision. What constraints applied.

## Decision
What was decided, stated plainly.

## Alternatives Considered
Other options and why they were not chosen.

## Consequences
What this makes easier, what it makes harder, what it forecloses.
```

Once written, add a one-line pointer to each ADR in [../Decisions.md](../Decisions.md).
