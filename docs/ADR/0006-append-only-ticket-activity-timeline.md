# ADR-0006: Append-Only Ticket Activity Timeline

**Status:** Accepted
**Date:** 2026-07-19

## Context

FR-TICK-12 requires every state-changing action on a ticket (status change, assignment, priority change, comment) to be recorded in an immutable activity timeline. SRS §8 Auditability requires this history to be append-only, not overwritten state. Acceptance Criterion 2 requires the complete history — every status change, assignment, and comment, with actor and timestamp — to be visible and consistent.

## Decision

Introduce a dedicated `TicketActivity` entity (append-only, no `UPDATE`/`DELETE` operations exposed at the repository or service layer — the repository for this entity exposes only `save`/`findBy*`, deliberately omitting update/delete methods). Every service-layer operation that changes ticket state writes both:

1. The mutation to the `Ticket` row itself (current-state view, used for list/dashboard queries — fast, denormalized present state).
2. An immutable `TicketActivity` row capturing `{ticketId, actorId, actionType, fieldChanged, oldValue, newValue, timestamp}`.

Both writes happen inside the **same transaction** (see [02-Architecture.md](../02-Architecture.md#request-lifecycle) transaction boundary), guaranteeing the current-state view and the audit trail never diverge.

This is a lightweight, targeted application of event-sourcing *principles* (append-only fact log) without adopting full event sourcing (the `Ticket` row remains the source of truth for current state; `TicketActivity` is a derived audit projection, not the sole source of truth) — full event sourcing was judged unjustified complexity for this scope (C2).

## Consequences

- **Positive:** Satisfies FR-TICK-12 and Acceptance Criterion 2 directly; the timeline is a straightforward indexed query (`WHERE ticket_id = ?  ORDER BY created_at`), not a replay/projection computation.
- **Positive:** Because the log is append-only and transactionally consistent with the state change, it also directly supports FR-FLOW-1 verification (a ticket reaching `RESOLVED` must show a prior `ASSIGNED` activity entry) and Acceptance Criterion 3 (no orphaned tickets).
- **Negative:** Slight write amplification (two inserts/updates per state-changing action instead of one) — negligible at the ticket volumes in scope (SRS §8 tens of thousands, not millions) and outweighed by audit correctness.
- **Alternatives considered:**
  - *Full event sourcing (Ticket state derived entirely by replaying events)* — rejected as premature complexity; revisit only if a future requirement (e.g., time-travel debugging, complex saga workflows) justifies it.
  - *Database-level audit triggers* — rejected: triggers are invisible to the application layer, bypass the Service-layer authorization/business-rule checks, and are harder to unit test than an explicit service-layer write.
