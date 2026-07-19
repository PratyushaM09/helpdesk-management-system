# ADR-0010: Optimistic Locking for Concurrent Ticket Updates

**Status:** Accepted
**Date:** 2026-07-19

## Context

A ticket can be concurrently acted on by multiple actors — a Support Engineer updating status while an Administrator reassigns it, or a User adding a comment while the engineer resolves it. FR-FLOW-1 requires invalid/skipped transitions to be rejected, which implies the system must detect and reject a state change based on stale data (e.g., resolving a ticket that was reassigned a moment ago, using the old assignee's context). SRS §8 Reliability requires predictable behavior under edge-case (here, concurrent) input.

## Decision

`Ticket` (and other frequently co-modified entities: `Comment` is append-only and doesn't need this) carries a `@Version` column (JPA optimistic locking). Every update path (status change, assignment, priority change) must supply the version it read; a concurrent conflicting write causes Hibernate to throw `OptimisticLockException`, translated by the Global Exception Handler ([02-Architecture.md](../02-Architecture.md#exception-strategy)) into a `409 Conflict` with a plain-language message ("This ticket was updated by someone else — please refresh and try again"), consistent with SRS §8's error-handling principle of actionable, plain-language errors.

## Consequences

- **Positive:** Prevents silent lost updates (e.g., an Administrator's reassignment being invisibly overwritten by a stale engineer status change) without the throughput cost and deadlock risk of pessimistic row locking, appropriate given tickets are read far more often than they're concurrently written.
- **Positive:** Requires no additional infrastructure (unlike distributed locks), fitting the single-database, modular-monolith architecture (ADR-0001).
- **Negative:** Clients (the SPA) must handle `409` by refetching and either re-showing the form or surfacing a merge prompt — a small but real frontend responsibility.
- **Alternatives considered:**
  - *Pessimistic locking (`SELECT ... FOR UPDATE`)* — rejected: ticket update conflicts are rare relative to read volume; pessimistic locks would serialize unrelated reads/writes unnecessarily and risk lock contention as concurrent user count grows (SRS §8 Scalability).
  - *No concurrency control (last-write-wins)* — rejected: directly risks the "no orphaned tickets" / workflow-integrity acceptance criterion (Acceptance Criterion 3) under concurrent access.
