# SDR-002: HttpOnly/Secure/SameSite Cookie Delivery for JWT

**Status:** Accepted
**Date:** 2026-07-19
**Extends:** ADR-0003 (Stateless JWT-Based Authentication)
**Related:** [07-Security-Architecture.md §5.8](../07-Security-Architecture.md#58-cookie-security)

## Decision
Deliver both the access token and refresh token exclusively as `HttpOnly`, `Secure`, `SameSite=Strict` cookies — never in a JSON response body, never in `localStorage`/`sessionStorage`, and never accessible to any JavaScript running on the page.

## Reason
ADR-0003 already committed to stateless JWT authentication for a decoupled SPA. That decision left open *how* the token reaches and is held by the browser — the choice between cookie storage and Web Storage (`localStorage`) has materially different security properties, and this SDR records that choice explicitly because it directly determines this system's exposure to XSS-driven session theft (threat #5/#7 in the threat model).

## Alternatives Considered
- **`localStorage`, token attached manually via `Authorization: Bearer` header:** the most common pattern in JWT tutorials and SPA starter templates. Rejected because any successful XSS (even a minor one, e.g., via a compromised dependency) gets trivial, complete, scriptable access to the token — `localStorage` has no same-origin-JS exemption. Given this system stores user-authored free text (ticket/comment content) that is rendered back to other users, XSS is a realistic threat category (Section 2 of the threat model) worth designing against even with strong output encoding as the primary defense (Section 10 of [08-Security-Controls.md](../08-Security-Controls.md#10-output-encoding-strategy)).
- **`SameSite=Lax` instead of `Strict`:** considered as a middle ground that still permits the cookie on top-level cross-site navigation (e.g., following a link from an email). Rejected for the access/refresh cookies specifically because this system has no legitimate use case for a cross-site-initiated authenticated request — `Strict` closes CSRF's primary vector (Section 9 of [03-Security.md](../03-Security.md#9-csrf)) at the browser level with zero functional cost here. `SameSite=Strict` is not applied to the (separately purposed) CSRF cookie in the same way since that cookie's value must still be readable by same-origin JS regardless.

## Pros
- XSS cannot read or exfiltrate the token — the single most consequential property, since it closes the highest-impact realistic threat.
- `SameSite=Strict` provides browser-level CSRF resistance as the first of two independent layers (paired with the double-submit token, SDR-007).
- No manual header-attachment code needed on every SPA request — the browser handles cookie transmission automatically and correctly for same-origin requests.

## Cons
- Requires CSRF protection as a second control (cookies are automatically sent, which is what makes CSRF possible in the first place) — accepted and addressed directly by SDR-007.
- Cross-origin API consumption (a future third-party/mobile client, SRS §15) cannot rely on browser-managed cookies the way a first-party SPA can — a non-browser client naturally uses the `Authorization: Bearer` header instead, which this design already supports as an alternative token-presentation path for non-cookie-capable clients, without weakening the cookie-based browser path.

## Future Impact
A future native mobile client or third-party API integrator (SRS §15) authenticates via `Authorization: Bearer <token>` instead of cookies — a mode the JWT authentication filter (Section 8.1 of [08-Security-Controls.md](../08-Security-Controls.md#81-authentication-filter-chain)) is designed to accept as an alternative credential-extraction source alongside the cookie, without any change to token issuance, verification, or the RBAC layer downstream.
