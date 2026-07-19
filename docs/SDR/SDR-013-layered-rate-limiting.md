# SDR-013: Layered Rate Limiting (Global IP, Auth-Specific, Per-User)

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [08-Security-Controls.md §12.5](../08-Security-Controls.md#125-rate-limiting)

## Decision
Apply rate limiting at three independent granularities: a coarse global per-IP budget across all API traffic, a strict per-IP-and-per-account budget specifically on `/auth/**` endpoints, and a per-user budget on write-heavy endpoints (ticket/comment/attachment creation).

## Reason
No single rate-limiting granularity defends against every relevant abuse shape in the threat model. A per-IP-only limit misses distributed credential stuffing (many IPs, few attempts each per IP, threat #14) targeting one account. A per-account-only limit misses a single attacker IP spraying attempts across many different accounts. A general API-wide limit alone under-protects the highest-value target (`/auth/**`) relative to normal traffic, while an auth-specific-only limit leaves business-logic-abuse-shaped flooding (threat #20/#21) on other endpoints unaddressed.

## Alternatives Considered
- **Single global per-IP rate limit only:** rejected as insufficient on its own for the reasons above — doesn't address distributed or per-account-targeted abuse patterns.
- **No rate limiting, rely on account lockout (SDR-005) alone:** rejected — account lockout only protects against attempts against *known, existing* accounts; it does nothing to bound the cost of, e.g., a flood of registration or forgot-password requests (which don't require a valid existing account) or general endpoint flooding unrelated to authentication.
- **Full API Gateway-level rate limiting only, none in the application:** rejected as the *sole* layer — while a gateway (Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)) is a valid future addition, this phase's deployment topology (SRS §12) doesn't yet include one, and even once it exists, application-level limiting remains valuable defense-in-depth (a gateway-level limit is typically coarser/IP-based only, while the application layer is the only place a per-*authenticated-user* budget can be meaningfully enforced, since the gateway doesn't know which user a request belongs to without decoding the JWT itself).

## Pros
- Each granularity closes a distinct abuse shape the others miss — genuine defense in depth, not redundant coverage of the same gap.
- Auth-specific stricter limits concentrate the tightest control on the highest-value target without over-throttling normal application usage elsewhere.
- `429` + `Retry-After` gives legitimate clients (including the SPA itself, under a burst of normal user activity) a clear, actionable signal rather than an opaque failure.

## Cons
- Requires tracking rate-limit state (counters) somewhere — for a single-instance deployment, in-memory is sufficient; for multiple horizontally-scaled instances, an in-memory-per-instance counter under-counts the true global rate (an attacker spread across instances via a load balancer could exceed the intended limit) — this is the specific, named trigger for introducing Redis as a shared counter store (Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)), not required before that scaling point is actually reached.

## Future Impact
The move from in-memory to Redis-backed rate-limit counters (once the application runs as more than one instance) is a storage-backend swap behind the same rate-limiting interface/logic — the three-granularity policy itself (global IP / auth-specific / per-user) does not change, only where the counters live.
