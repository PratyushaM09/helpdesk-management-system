# ADR-0003: Stateless JWT-Based Authentication (over server-side sessions)

**Status:** Accepted
**Date:** 2026-07-19

## Context

SRS §15 lists JWT-based stateless auth as an anticipated future direction "if moving toward a decoupled API + SPA/mobile architecture" — and ADR-0002 has already committed to exactly that architecture (React SPA over a REST API). SRS §8 requires session tokens to expire, and FR-AUTH-3/4/8 require login, logout, and lockout/throttling after failed attempts.

## Decision

Use **stateless JWT access tokens + rotating refresh tokens**, not server-side (`HttpSession`) authentication, from the outset — not deferred to a later phase.

- Access token: short-lived (15 minutes), signed (HS512 in dev / RS256 in prod via asymmetric keys), carries subject (user ID), role, and a token version claim.
- Refresh token: longer-lived (7 days), opaque, stored server-side (hashed) in a `refresh_token` table so it can be revoked (logout, password change, admin-forced logout) — this is what makes "logout invalidates the active session" (FR-AUTH-4) possible despite JWTs being otherwise unrevokable by nature.
- Delivered to the SPA via an `HttpOnly`, `Secure`, `SameSite=None` cookie (not `localStorage`) to remove XSS token-theft as an attack vector — see [03-Security.md](../03-Security.md#session-management). (`SameSite` revised from `Strict` to `None` per the SDR-002 amendment, since deployment placed frontend and backend on different subdomains.)

## Consequences

- **Positive:** No server-side session store needed → horizontally scalable stateless backend from day one (SRS §8 Scalability), each request independently authenticates without a shared session cache — directly compatible with a future Redis-backed session store or multi-instance deployment (SRS §15) without redesign.
- **Positive:** Matches ADR-0002's decoupled SPA; the same auth mechanism extends to a future native mobile client or third-party API consumer (SRS §15) with no backend change.
- **Positive:** OAuth2/Google login (SRS §15) becomes an additional token-issuance path feeding the same JWT contract, not a parallel auth system.
- **Negative:** Immediate revocation of an access token (as opposed to the refresh token) is not possible before natural expiry; mitigated by keeping access-token lifetime short (15 min) and including a `tokenVersion` claim checked against the user record, so an admin-forced "log out everywhere" (e.g., after role change) increments the version and invalidates all outstanding access tokens on next check.
- **Alternatives considered:**
  - *Server-side session (`HttpSession` + `JSESSIONID`)* — rejected: requires sticky sessions or a shared session store to scale horizontally (extra infra now for a benefit only needed later), and doesn't naturally extend to a future mobile client.
  - *JWT in `localStorage`* — rejected: exposes the token to any successful XSS, violating the security-first mandate; `HttpOnly` cookie delivery is strictly safer for a browser SPA client.
