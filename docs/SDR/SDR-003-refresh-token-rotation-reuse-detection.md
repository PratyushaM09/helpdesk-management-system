# SDR-003: Refresh Token Rotation with Reuse Detection

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [07-Security-Architecture.md §5.3](../07-Security-Architecture.md#53-session-renewal)

## Decision
Every use of a refresh token issues a brand-new refresh token and immediately marks the used one consumed/replaced. If a refresh token that has already been consumed is ever presented again, the entire token family (every token descended from the same original login) is revoked immediately, forcing full re-authentication.

## Reason
A long-lived refresh token (7 days) is a meaningfully valuable target if intercepted (e.g., via a compromised network, a logging misconfiguration, or a browser-extension-level leak despite `HttpOnly` protection). Rotation with reuse detection is the standard OAuth2/OIDC-community-recommended mitigation for exactly this scenario: it turns a stolen-but-not-yet-used token into a ticking, self-revealing liability rather than a silent, indefinitely-replayable credential.

## Alternatives Considered
- **Static (non-rotating) refresh token, valid until its 7-day expiry:** simpler to implement, but a captured token remains fully usable by an attacker for up to a week with no detection signal — the legitimate user's own continued use doesn't invalidate the attacker's copy, since both would independently "work." Rejected as leaving too large a silent-exploitation window for a system storing internal support-ticket data (which itself may contain sensitive operational detail per ticket content, even though this phase's data classification is internal, not public-facing).
- **Rotation without reuse detection:** rotating the token still helps (shortens the effective lifetime of any single token value) but without detecting a stolen-token replay, an attacker racing ahead of the legitimate user's next refresh could still ride along token generation to token generation. Reuse detection specifically closes this by treating *any* replay of an already-superseded token as a breach signal, not just an expected race to be silently resolved.
- **Immediate single-use access tokens only, no refresh token at all (re-login every 15 minutes):** rejected as an unacceptable usability regression for an internal productivity tool (Personas, SRS §5) with no offsetting security benefit over the rotation-with-detection design.

## Pros
- Converts token theft from a silent, week-long exposure into a self-detecting event bounded to the interval between the legitimate user's refresh calls (typically minutes, since the SPA refreshes transparently on access-token expiry, every 15 minutes).
- No additional user-facing friction — rotation is entirely transparent to the legitimate user's normal usage pattern.
- Reuse-detection revocation is a strong, unambiguous incident signal that can be logged and alerted on (Section 16 of [09-Security-Operations.md](../09-Security-Operations.md#16-logging-strategy)) as a distinct, high-priority security event.

## Cons
- Slightly more server-side state to manage (a `RefreshToken` table with a `replaced_by_id` chain, [05-Database.md §2](../05-Database.md#2-entity-relationship-diagram)) than a fully stateless refresh scheme — an accepted, minor tradeoff against the stateless-access-token design (ADR-0003), since refresh tokens were already server-side-tracked for the independent reason of supporting logout/revocation (Section 3.3 of [07-Security-Architecture.md](../07-Security-Architecture.md#33-logout-flow-fr-auth-4)).
- A legitimate network hiccup causing a client to retry a refresh call with an already-consumed token (a race, not an attack) could trigger a false-positive revocation; mitigated by a short grace window (e.g., a few seconds) during which the immediately-prior token in the chain is still accepted, before treating a replay as reuse.

## Future Impact
This pattern generalizes directly to any future federated-auth token type (OAuth2 refresh tokens obtained via a Google/Microsoft login, Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)) — the internal `RefreshToken` table and rotation logic are auth-method-agnostic, since they operate on this application's own issued refresh token regardless of how the *original* authentication was performed.
