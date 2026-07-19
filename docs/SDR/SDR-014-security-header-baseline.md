# SDR-014: Security Header Baseline Applied to Every Response

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [08-Security-Controls.md §13.8](../08-Security-Controls.md#138-http-header-strategy)

## Decision
Apply a fixed set of security headers (`Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`, `Content-Security-Policy`, `Referrer-Policy`, `Permissions-Policy`, `Cache-Control: no-store` on sensitive responses, `X-Permitted-Cross-Domain-Policies`) to **every** API response by default — not selectively on HTML-serving routes only, since this API serves none.

## Reason
Several of these headers are commonly (and incorrectly) treated as only relevant to HTML-rendering applications and are skipped on "pure JSON API" backends. This is a mistake in this system's specific context: the response from any endpoint could in principle be loaded in a non-XHR context by a misconfigured or malicious third party (e.g., an attacker framing an endpoint URL directly, or a browser MIME-sniffing an unexpected response type), and several of these headers (`X-Content-Type-Options`, `X-Frame-Options`/`frame-ancestors`) specifically defend against exactly that class of scenario. Treating header application as a blanket, response-type-agnostic default (rather than a per-route decision) also removes an entire class of "we forgot to add the header to this one new controller" gap.

## Alternatives Considered
- **Apply headers only to the small number of HTML/document-serving responses (none currently exist, but the attachment-download endpoint is response-type-adjacent):** rejected — creates a per-endpoint decision ("does this route need security headers?") that is easy to get wrong on a new endpoint, versus a single global response filter that requires no per-route decision at all.
- **Rely on a future reverse-proxy/API Gateway to add these headers instead of the application:** considered, since Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap) does anticipate a future reverse proxy. Rejected as the *sole* mechanism for the same reason SDR-013 rejected gateway-only rate limiting: the application should not have a security property that only holds true *if* a specific piece of future infrastructure is correctly configured in front of it — headers are applied at the application layer as the guaranteed baseline, with a future proxy layer allowed to add to (never rely on to provide) this baseline.

## Pros
- Zero per-route decision-making — a new endpoint automatically inherits the full header baseline via a single response filter, consistent with the Secure by Default goal ([07-Security-Architecture.md §1](../07-Security-Architecture.md#1-security-goals)).
- Defends the API surface even against unconventional/unexpected client behavior (a browser navigated directly to an API URL, a misconfigured third-party integration attempting to frame or embed a response).

## Cons
- A small, fixed response-size overhead (a handful of header bytes) on every response — negligible relative to any realistic payload size in this system.
- CSP specifically requires the rollout discipline described in SDR-008 (report-only first) precisely because it is the one header in this set with realistic potential to break something if misconfigured — the others in this baseline carry no such rollout risk and can be enabled outright.

## Future Impact
When a reverse proxy/API Gateway is introduced (Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)), it may add its own additional headers (or reinforce these at the edge) — the application-level baseline remains unchanged and is not removed, consistent with the defense-in-depth principle of not retiring an application-layer control just because an infrastructure-layer equivalent is added.
