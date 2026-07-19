# SDR-008: Strict Content Security Policy with Report-Only Rollout

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [08-Security-Controls.md §13.8](../08-Security-Controls.md#138-http-header-strategy), [§13.11](../08-Security-Controls.md#1311-content-security-policy)

## Decision
Ship a strict `Content-Security-Policy` (`default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'`) — no inline scripts, no third-party script origins, no framing. Roll out in `Content-Security-Policy-Report-Only` mode first (with a violation-reporting endpoint) before switching to enforced mode, so legitimate application behavior the policy would inadvertently block is discovered before it breaks production.

## Reason
CSP is the defense-in-depth backstop for XSS (threat #7) — output encoding (Section 10 of [08-Security-Controls.md](../08-Security-Controls.md#10-output-encoding-strategy)) is the primary control, but CSP bounds the *impact* of any XSS that nonetheless occurs (e.g., via a compromised third-party frontend dependency, which output encoding alone cannot prevent since the malicious code would already be first-party-served JavaScript at that point). A strict policy with no `unsafe-inline`/`unsafe-eval` exception is meaningfully more protective than a loose one — the loose version is common in the wild specifically because retrofitting a strict policy onto an app with pre-existing inline scripts is hard; building the frontend against a strict policy from the start (this being a greenfield build, per [01-SRS.md](../01-SRS.md) Project Status) avoids that retrofit cost entirely.

## Alternatives Considered
- **No CSP at all:** rejected — a near-zero-cost control (for a greenfield SPA with no inline-script legacy debt) that materially reduces XSS blast radius; omitting it would leave a known, cheap OWASP-recommended control unused for no offsetting benefit.
- **Loose policy allowing `unsafe-inline`:** rejected — `unsafe-inline` for scripts specifically neutralizes CSP's primary XSS-mitigating property (an injected `<script>` tag executes exactly as freely as legitimate inline code would); if the frontend build tooling (React/Vite, ADR-0002) ever requires inline styles for a specific component, `style-src` is the narrower place to consider a scoped, nonce-based exception — never `script-src`.
- **Enforced mode from day one, no report-only phase:** rejected as operationally risky — CSP is notorious for breaking overlooked legitimate resource loads (a forgotten font/image origin, an analytics snippet) in ways that are hard to predict from code review alone; a report-only rollout window (monitored during the security testing phase, Section 19.8 of [10-Security-Assurance.md](../10-Security-Assurance.md#198-xss-testing)) catches these before they cause a production outage-shaped incident.

## Pros
- Meaningfully bounds real-world XSS impact even where output encoding has a gap.
- Report-only rollout removes the "will this break something" risk from adopting a strict policy.
- Greenfield build avoids the common retrofit difficulty of tightening CSP on a legacy app.

## Cons
- Any future legitimate need for a third-party script (e.g., an analytics or support-widget integration) requires a deliberate, reviewed CSP exception (adding a specific origin to `script-src`, or a nonce/hash mechanism) rather than being freely addable — an intentional friction, not an oversight.
- Requires frontend build discipline (no inline `<script>`/`<style>` tags, no `eval`-family usage) — consistent with modern React/Vite conventions already, so low practical cost.

## Future Impact
Any future third-party integration (a Google/Microsoft login widget, an embedded analytics or support-chat script, Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)) requires an explicit CSP allow-list addition as part of that integration's own review — CSP maintenance becomes a standing checklist item for any new external dependency, not a one-time setup task.
