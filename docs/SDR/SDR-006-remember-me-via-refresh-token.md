# SDR-006: "Remember Me" via Sliding Refresh Token, Not a Separate Mechanism

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [07-Security-Architecture.md §3.7](../07-Security-Architecture.md#37-remember-me), [§5.7](../07-Security-Architecture.md#57-remember-me-strategy)

## Decision
Do not implement a separate "Remember Me" opt-in checkbox or a distinct, longer-lived remember-me token type. The standard 7-day rotating refresh token (SDR-003) already provides persistent-login-across-browser-restarts behavior for every user by default.

## Reason
Classic "Remember Me" implementations (a long-lived, separately-issued cookie, historically often weaker than the primary session mechanism) are a well-documented source of real-world vulnerabilities: they frequently bypass controls the primary login path enforces (rate limiting nuance, and critically, future MFA — a remember-me cookie that skips a second factor defeats the entire point of adding one). Rather than build a second, parallel, weaker authentication path, this system's single session mechanism is simply designed to already have "remember me"-equivalent duration (7 days, sliding via rotation) baked into the default behavior.

## Alternatives Considered
- **Opt-in checkbox controlling a separate long-lived cookie (e.g., 30 days) distinct from the standard session:** the conventional pattern. Rejected — introduces a second token type/verification path to secure and test, and creates exactly the future MFA-bypass risk described above unless carefully re-integrated at that time (extra design debt deferred for a benefit — user choice of session length — that the persona set in SRS §5 doesn't clearly need).
- **No persistent session at all (re-login every browser restart):** rejected as excessive friction for an internal daily-use productivity tool with no corresponding security benefit over the rotating-refresh-token design.

## Pros
- One session mechanism to secure, test, and reason about — not two.
- No future MFA-bypass trapdoor to remember to close later.
- Users get "stay logged in" behavior by default without needing to discover/enable a checkbox.

## Cons
- No user-facing choice to use a *shorter*-than-default session on a shared/public machine — mitigated by explicit logout (Section 3.3 of [07-Security-Architecture.md](../07-Security-Architecture.md#33-logout-flow-fr-auth-4)) always being available and by this being an internal-workforce tool (SRS §12) rather than a public-kiosk-use scenario where this gap would matter more.

## Future Impact
When MFA/WebAuthn is introduced (Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)), the second-factor challenge applies uniformly to every login that establishes a new refresh-token family — there is no separate "remembered" path that could accidentally skip it, because none exists.
