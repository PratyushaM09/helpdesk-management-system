# SDR-007: Double-Submit CSRF Protection over Server-Side Synchronizer Tokens

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [03-Security.md §9](../03-Security.md#9-csrf), [08-Security-Controls.md §13.1](../08-Security-Controls.md#131-csrf-protection)

## Decision
Use the double-submit cookie pattern for CSRF protection: a non-`HttpOnly` CSRF token cookie set at login, echoed by the SPA into a custom `X-CSRF-Token` request header on every state-changing request, validated server-side for equality — originally layered on top of `SameSite=Strict` cookies (SDR-002), which was later revised to `SameSite=None` (SDR-002 amendment) for the cross-subdomain deployment, leaving this double-submit check as the sole CSRF control.

## Reason
Cookie-based authentication (SDR-002) reintroduces CSRF exposure that a pure `Authorization: Bearer`-header scheme wouldn't have. A CSRF defense is therefore required, and the specific pattern matters: the classic "synchronizer token" pattern stores the expected token server-side per session, which requires server-side session state — directly in tension with this system's stateless-JWT architecture (ADR-0003). The double-submit pattern achieves an equivalent security property without any server-side session storage: it relies on the fact that a cross-site attacker's forged request can trigger the browser to *send* the cookie automatically, but cannot *read* the cookie's value to construct the matching header, since cross-origin JavaScript cannot access another origin's cookies (same-origin policy).

## Alternatives Considered
- **Server-side synchronizer token pattern:** rejected — requires a server-side token store keyed by session, reintroducing the statefulness ADR-0003 deliberately eliminated; would need its own storage/expiry/cleanup design for no meaningful security benefit over double-submit in this system's specific cookie-auth configuration.
- **Rely on `SameSite=Strict` alone, no explicit CSRF token:** seriously considered, since `SameSite=Strict` alone closes the vast majority of realistic CSRF scenarios in modern browsers. Rejected as the *sole* control because `SameSite` enforcement has historically had browser-specific edge cases and version-rollout inconsistencies, and because some legitimate top-level-navigation-adjacent flows (a user opening a link from an internal wiki/email pointing into the app) could be affected by `Strict` in ways a defense-in-depth token check safely backstops without weakening the cookie policy itself.
- **Custom request header presence alone (no token equality check), relying on the fact that simple cross-site forms cannot set custom headers:** a lighter-weight variant. Rejected in favor of full token-equality validation because it costs almost nothing extra to implement correctly and closes a marginally larger set of edge cases (some vectors can set an XHR/fetch header if a CORS misconfiguration exists elsewhere — Section 10 of [03-Security.md](../03-Security.md#10-cors) — so equality-checking a value cross-origin JS cannot read is a stronger property than header-presence-checking alone).

## Pros
- No server-side session state required — fully consistent with the stateless architecture (ADR-0003).
- Originally two independent CSRF defenses (`SameSite=Strict` + double-submit) — a gap in browser `SameSite` enforcement was still caught by the token check, and vice versa. Since the SDR-002 amendment moved `SameSite` to `None` for cross-subdomain deployment, the double-submit token is now the sole CSRF control, but it remains a complete defense on its own.
- Standard, well-understood pattern with first-class Spring Security support.

## Cons
- The CSRF cookie itself must be non-`HttpOnly` (JS-readable) — a narrower exception to the "no token is JS-accessible" principle (SDR-002), scoped deliberately to a token whose sole purpose is anti-CSRF, not authentication (possessing it alone grants no access — it is meaningless without the paired `HttpOnly` auth cookies).
- Adds one required header the SPA's HTTP client must attach to every mutating request — a small, one-time frontend integration cost.

## Future Impact
A future non-browser API consumer (mobile app, third-party integration, SRS §15) authenticating via `Authorization: Bearer` (SDR-002's noted alternative path) is not subject to CSRF at all (CSRF is fundamentally a browser-cookie-auth phenomenon) — the CSRF layer is scoped specifically to cookie-authenticated requests and does not need to be extended or adapted for that future client type.
