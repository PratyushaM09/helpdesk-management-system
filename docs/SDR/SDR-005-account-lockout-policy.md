# SDR-005: Account Lockout Policy (5 Attempts / 15-Minute Window)

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [07-Security-Architecture.md §3.9](../07-Security-Architecture.md#39-account-locking-fr-auth-8), FR-AUTH-8

## Decision
Lock an account after 5 consecutive failed login attempts, for a 15-minute sliding window; the failed-attempt counter resets only on a subsequent successful login, not merely on window expiry. An Administrator may manually unlock an account early.

## Reason
FR-AUTH-8 mandates lockout/throttling after repeated failures. The specific thresholds (5 / 15 minutes) balance two competing risks directly named in the threat model: too permissive a threshold leaves brute-force (#13) and credential-stuffing (#14) practical; too aggressive a threshold turns the control itself into a denial-of-service vector against legitimate users (an attacker who knows a victim's email can lock them out on demand simply by attempting bad logins) — 5/15 is a widely-used industry baseline (aligned with NIST 800-63B's throttling guidance) that meaningfully slows automated guessing without making lockout itself a practical griefing tool for a small number of mistyped-password incidents.

## Alternatives Considered
- **No lockout, CAPTCHA-only after N failures:** rejected — CAPTCHA adds user friction and third-party dependency without directly bounding attempt volume the way a hard lockout does; a combined approach (CAPTCHA *and* rate limiting, no full lockout) was also considered but judged unnecessarily complex for this phase given the internal, non-public-registration deployment context (SRS §12).
- **Permanent lockout requiring Administrator intervention for every occurrence:** rejected — creates an operational burden disproportionate to the internal-tool threat level, and is itself a more effective denial-of-service vector against legitimate users than the sliding 15-minute window.
- **IP-based lockout instead of account-based:** rejected as the sole mechanism — an attacker distributing attempts across many source IPs (or a legitimate shared-NAT office network) makes IP-based lockout both bypassable and prone to false positives; account-based lockout is paired with IP-aware rate limiting (SDR-013) as a complementary, not alternative, control.

## Pros
- Directly satisfies FR-AUTH-8 and Acceptance Criterion coverage for brute-force resistance.
- Sliding-window-plus-counter-reset-on-success design prevents an attacker from indefinitely probing "one attempt short of lockout" forever without ever facing an actual reset requirement.
- Administrator override provides an operational escape hatch for legitimate lockout incidents without weakening the automatic policy.

## Cons
- A malicious actor who knows a victim's email can still trigger a 15-minute denial-of-service against that one account's ability to log in — an accepted, bounded cost (15 minutes, self-clearing) against the larger benefit of brute-force resistance; full mitigation of this specific griefing vector would require a fundamentally different (and more complex) design, judged not justified at this phase's threat level.

## Future Impact
Once MFA (Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)) is introduced, lockout policy may be relaxed for MFA-enrolled accounts specifically (a correct password alone is no longer sufficient for account takeover, reducing the urgency of aggressive password-guessing throttling for that subset of users) — a future, explicitly-scoped policy refinement, not required by this phase's design.
