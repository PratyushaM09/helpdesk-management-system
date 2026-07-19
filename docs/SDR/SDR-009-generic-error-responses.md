# SDR-009: Generic, Catalog-Based Error Messages (No Exception-Detail Passthrough)

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [09-Security-Operations.md §18](../09-Security-Operations.md#18-error-handling), SRS Constraint C4

## Decision
Every client-facing error message is drawn from a fixed, reviewed message catalog keyed by `errorCode` — never constructed from `exception.getMessage()`, a stack trace, or any other raw internal-exception detail. A single `GlobalExceptionHandler` is the only place an exception becomes an HTTP response.

## Reason
Improper exception handling (threat #25) and information disclosure (threat #26) are among the most common real-world sources of accidental sensitive-data leakage — not through a deliberate attack, but through an ordinary developer convenience shortcut (returning `ex.getMessage()` directly) that happens to leak a SQL fragment, an internal file path, or a framework class name the day some new exception type is introduced. SRS Constraint C4 already mandates plain-language, non-technical error messages; this SDR is the formal security decision that makes that mandate structurally enforced rather than dependent on every developer remembering it on every new exception type.

## Alternatives Considered
- **Per-exception custom messages written inline at each throw site:** rejected — scales poorly (a message-wording decision is made ad hoc, dozens of times, by whoever happens to be writing that code path) and provides no single place to review "does any of this leak anything" before shipping.
- **Return `exception.getMessage()` in `dev`/`test` only, catalog-based in `prod`:** considered, since it would aid local debugging. Rejected as the default — a per-profile behavior difference here risks the exact failure mode this control exists to prevent (a `prod` deploy accidentally running with the wrong profile flag, Section 17.7 of [09-Security-Operations.md](../09-Security-Operations.md#177-production-configuration-hardening-checklist)); the `traceId`-correlated server-side log (Section 16 of [09-Security-Operations.md](../09-Security-Operations.md#16-logging-strategy)) already gives a developer full detail without needing a client-visible profile-dependent code path that could be misconfigured.

## Pros
- Structurally impossible for a *new* exception type to leak detail by omission — the generic `5xx` catch-all (Section 18.2 of [09-Security-Operations.md](../09-Security-Operations.md#182-error-response-structure)) guarantees a safe default even for an exception nobody explicitly planned for.
- Consistent, reviewable message catalog — a security/UX reviewer can audit every possible client-facing string in one place.
- `errorCode` + `traceId` together give both the client (a stable thing to branch on) and the engineer (a precise log-correlation key) what they actually need, without the message itself needing to carry either concern.

## Cons
- Requires discipline to add a catalog entry (rather than an inline string) whenever a genuinely new business-error category is introduced — a minor, deliberate process cost accepted for the security/consistency benefit.

## Future Impact
As new modules/features are added (per the roadmap in [02-Architecture.md §21](../02-Architecture.md#21-future-architecture-roadmap)), each introduces its own `errorCode` catalog entries following the same pattern — the `GlobalExceptionHandler`'s structure does not need to change, only extend.
