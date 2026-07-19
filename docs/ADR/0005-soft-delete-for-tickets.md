# ADR-0005: Soft Delete for Tickets (and Category deactivation)

**Status:** Accepted
**Date:** 2026-07-19

## Context

SRS FR-TICK-5 and Assumption A4 require ticket deletion by an Administrator to be a soft delete — retained for audit, hidden from normal views — to preserve historical reporting integrity (FR-REP-1 needs complete historical data). FR-PRI-3 similarly requires categories to be deactivatable without deleting historical tickets that reference them.

## Decision

- `Ticket` carries a `deleted_at TIMESTAMP NULL` (and `deleted_by`) column rather than being physically removed. All standard repository queries apply a `deleted_at IS NULL` predicate by default (via Hibernate `@SQLRestriction`/`@Where`-equivalent or a default `Specification`), so soft-deleted tickets transparently disappear from normal list/search/dashboard views without every service method re-implementing the filter.
- `Category` carries an `active BOOLEAN` flag instead of deletion. Deactivated categories are excluded from *new*-ticket category pickers but remain valid, joinable data for historical tickets and reports (FR-PRI-3).
- A true hard-delete/purge capability is explicitly **not** built in this phase (Assumption A4) — it is named in the roadmap ([02-Architecture.md](../02-Architecture.md#future-architecture-roadmap)) as a separate, tightly-scoped operation (e.g., a scheduled data-retention job, SRS §17.10) if/when a retention policy is defined.

## Consequences

- **Positive:** Reports (FR-REP-1) and the audit trail (SRS §8 Auditability) always reconcile against complete historical data, satisfying Acceptance Criterion 7 even after admin deletions.
- **Positive:** Deletion is reversible by direct data correction if performed in error — an operational safety net not available with hard delete.
- **Negative:** Every query path must consistently apply the not-deleted filter, or soft-deleted data leaks into views; mitigated by making the filter the *default* at the repository layer (opt-out via an explicit `includeDeleted` query, restricted to Administrator-only audit views) rather than opt-in per query.
- **Negative:** Unique constraints (e.g., ticket number) must account for soft-deleted rows still occupying the constraint space — accepted, since ticket numbers are never reused by design (FR-TICK-1).
- **Alternatives considered:**
  - *Hard delete* — rejected outright by FR-TICK-5/A4.
  - *Separate archive table* — rejected as unnecessary complexity at this scale; a boolean/timestamp flag with a default query filter achieves the same isolation with far less mapping overhead, and can be revisited (partitioning by `deleted_at`) if ticket volume growth (SRS §8 Scalability) ever demands it.
