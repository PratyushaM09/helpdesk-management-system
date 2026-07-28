# 03 — Security Design

## HelpDesk Management System

| | |
|---|---|
| **Document Version** | 1.0 |
| **Status** | Accepted — Architecture Phase |
| **Traces to** | [01-SRS.md §8](01-SRS.md#8-non-functional-requirements) (Security NFR), Acceptance Criteria 1, 6, 10 |
| **Related** | [02-Architecture.md](02-Architecture.md) (component flows), ADR-0003, ADR-0004, ADR-0008 |
| **Superseded by** | This document is the architecture-phase security summary. The dedicated **Enterprise Security Architecture Document (SecAD)** — [07-Security-Architecture.md](07-Security-Architecture.md) · [08-Security-Controls.md](08-Security-Controls.md) · [09-Security-Operations.md](09-Security-Operations.md) · [10-Security-Assurance.md](10-Security-Assurance.md) · [SDR/](SDR/) — is the authoritative, governing security design going forward (threat model, full auth/session/RBAC detail, OWASP mapping, security testing strategy, Security Decision Records). This document remains valid as an accurate summary and is not contradicted by the SecAD, only expanded. |

---

## Table of Contents

1. Threat Model Summary
2. Authentication
3. Session Management
4. Password Policy & Encryption
5. RBAC Matrix
6. Authorization Mechanics
7. Security Filters & Interceptors
8. Method Security
9. CSRF
10. CORS
11. Input Validation & Output Encoding
12. File Upload Security
13. Rate Limiting & Account Lockout
14. Data Protection (At-Rest / In-Transit)
15. Security Logging & Auditing
16. Dependency & Configuration Hardening
17. Mapping to Non-Functional Requirements

---

## 1. Threat Model Summary

In scope for this phase (single-organization, internal helpdesk, per SRS §12): credential-based account compromise, privilege escalation between the three roles, unauthorized cross-user data access (a User reading another User's ticket), malicious file upload, injection (SQL/XSS), session/token theft, and denial of service via unbounded resource consumption (unpaginated queries, unlimited login attempts, unlimited upload size). Out of scope for this phase (explicitly, per SRS §12/§15): multi-tenant isolation, SSO/OAuth federation trust boundaries, and infrastructure-level (network/cloud) hardening, which are future-scope items layered onto this design without requiring its rework.

---

## 2. Authentication

Mechanism: **stateless JWT** (ADR-0003). Flow detail in [02-Architecture.md §8](02-Architecture.md#8-authentication-flow); this section specifies the concrete policy.

- **Registration (FR-AUTH-1):** Public self-registration creates a `User` with role `USER` only — the role field is never client-settable; `SUPPORT_ENGINEER` and `ADMIN` accounts are created exclusively by an existing Administrator through the admin-only user-management endpoint (Assumption A1). This is enforced at the DTO level: `RegisterRequest` has no `role` field at all, not merely a validated one.
- **Email verification (FR-AUTH-2):** Account is created in an `UNVERIFIED` state; a signed, time-limited (24h) verification token is issued (in-app notification per Assumption A7; email-ready per ADR-0007). Login is permitted only for `VERIFIED` accounts — an unverified login attempt returns a specific, actionable error ("Please verify your email — we've sent you a link") rather than a generic auth failure.
- **Login (FR-AUTH-3):** Email + password against BCrypt hash (Section 4). Successful login issues an access token (15 min) and a refresh token (7 days) per ADR-0003.
- **Logout (FR-AUTH-4):** Revokes the refresh token server-side (deletes/invalidates its hashed record) and clears both cookies. The still-valid access token remains cryptographically valid until natural expiry (max 15-minute exposure window) — an accepted tradeoff documented in ADR-0003.
- **Forgot / reset password (FR-AUTH-5):** Time-limited (1h), single-use, signed reset token delivered via the notification abstraction (in-app now, email-ready). Reset invalidates all existing refresh tokens for that user (forces re-login everywhere) — a compromised-password scenario should not leave old sessions alive.
- **Change password (FR-AUTH-6):** Requires re-entry of the current password even though the user is already authenticated — prevents a hijacked-but-unattended session from silently taking over the account by changing the password alone.
- **Password strength (FR-AUTH-7):** Enforced by a custom `@StrongPassword` Bean Validation constraint (Section 14 of [02-Architecture.md](02-Architecture.md#14-validation-strategy)): minimum 10 characters, at least one uppercase, one lowercase, one digit, one symbol. Checked at registration, reset, and change.
- **Lockout/throttling (FR-AUTH-8):** See Section 13.

---

## 3. Session Management

- No server-side `HttpSession` is used (ADR-0003) — the application tier is fully stateless.
- **Access token:** JWT, 15-minute expiry, signed RS256 in `prod` (asymmetric — verification key can be distributed to other services later without sharing the signing secret, relevant to the microservices roadmap item), HS512 acceptable in `dev`. Claims: `sub` (user id), `role`, `tokenVersion`, `iat`, `exp`. Delivered as an `HttpOnly`, `Secure`, `SameSite=None` cookie (see SDR-002 amendment — frontend/backend are cross-subdomain) — never accessible to JavaScript, closing the primary XSS-token-theft vector (ADR-0003).
- **Refresh token:** Opaque random value (not a JWT), stored server-side only as a salted hash (never plaintext) in a `refresh_token` table keyed by user, with `expires_at` and `revoked_at`. Rotated on every use (old refresh token invalidated the moment a new one is issued) — a replayed stolen refresh token is usable exactly once before detection (reuse of an already-rotated token triggers immediate revocation of the entire token family, a standard breach-detection pattern).
- **Forced global logout:** Incrementing a user's `tokenVersion` (on password change, password reset, or an Administrator-initiated "log out this user everywhere") invalidates every outstanding access token on next verification, without needing a token blocklist.
- **Idle/absolute expiry:** Refresh token's 7-day absolute expiry forces re-authentication at least weekly regardless of activity, satisfying SRS §8's "session tokens shall expire."

---

## 4. Password Policy & Encryption

- Passwords are hashed with **BCrypt** (Spring Security's `BCryptPasswordEncoder`, cost factor 12 in `prod`, lower in `test` for speed) — never reversible encryption, never stored plaintext (SRS §8 explicit requirement).
- The password hash column is never included in any DTO, never logged (Section 15), and excluded from entity `toString()`/JSON serialization by construction (it simply has no mapper path to any response DTO — ADR-0009's allow-list mapping model makes this a structural guarantee, not a per-field annotation someone can forget).
- Password comparison always uses the encoder's constant-time `matches()` — never a manual string comparison (timing-attack resistance).

---

## 5. RBAC Matrix

Three roles: `USER`, `SUPPORT_ENGINEER`, `ADMIN`. Permissions are resource-and-ownership-aware, not just role-flat — this is exactly why enforcement is two-layered (ADR-0004, Section 6).

| Action | USER | SUPPORT_ENGINEER | ADMIN |
|---|---|---|---|
| Register / login / manage own profile | ✅ (self only) | ✅ (self only) | ✅ (self only) |
| Create ticket | ✅ | — | — |
| View ticket | ✅ own only (creator) | ✅ assigned only | ✅ all |
| Update ticket description/attachments (pre-triage states only, FR-TICK-4) | ✅ own only | — | ✅ all |
| Change ticket status / priority / category (triage) | — | ✅ assigned only | ✅ all |
| Assign / reassign ticket | — | ✅ self-assign, where enabled | ✅ any engineer, any ticket |
| Close own resolved ticket | ✅ own only | — | ✅ all |
| Reopen closed ticket (within window) | ✅ own only | — | ✅ all |
| Soft-delete ticket | — | — | ✅ |
| Comment on ticket | ✅ own tickets | ✅ assigned tickets | ✅ all |
| Upload attachment | ✅ own tickets | ✅ assigned tickets | ✅ all |
| Download attachment | ✅ if ticket-visible | ✅ if ticket-visible | ✅ |
| View own notifications | ✅ | ✅ | ✅ |
| View dashboard (role-specific) | ✅ User dashboard | ✅ Engineer dashboard | ✅ Admin dashboard |
| Manage users / roles | — | — | ✅ |
| Manage categories / priorities | — | — | ✅ |
| Generate / export reports | — | — | ✅ |
| View admin audit log | — | — | ✅ |

This matrix is the source of truth both the URL-level filter rules and the method-level `@PreAuthorize`/`PermissionEvaluator` checks (Section 6) are implemented against — kept as a single reviewed table so a permission change is a one-place decision, then propagated to two enforcement points deliberately (ADR-0004), not two independently-maintained sources of truth.

---

## 6. Authorization Mechanics

Two enforced layers (ADR-0004; flow diagram in [02-Architecture.md §9](02-Architecture.md#9-authorization-flow)):

1. **URL-level (coarse):** Spring Security `SecurityFilterChain` route-matcher rules, e.g. `/api/v1/admin/**` → `hasRole('ADMIN')`, `/api/v1/reports/**` → `hasRole('ADMIN')`. Rejects wrong-role calls before any Service or Repository code executes (fail fast, also a performance/DoS-resistance benefit — Section 13).
2. **Method-level (fine, ownership-aware):** `@PreAuthorize` on Service.impl methods, e.g. `@PreAuthorize("hasRole('ADMIN') or @ticketPermissionEvaluator.canView(#ticketId, authentication)")`. The custom `PermissionEvaluator` implementations (one per module needing ownership checks: Ticket, Comment, Attachment) load only the minimal ownership fields (creator id, assignee id) needed to decide — never the full entity — keeping this check cheap.

Both layers read role/permission constants from the single `constants` package (Section 5 of [02-Architecture.md](02-Architecture.md#5-package-structure)) — a role name string is never duplicated as a literal in two places.

---

## 7. Security Filters & Interceptors

Request pipeline order (Servlet filters run before Spring's `DispatcherServlet`; interceptors run within it — Section 5 of [02-Architecture.md](02-Architecture.md) explains why these are separate packages):

1. **CORS filter** — rejects cross-origin requests not from the configured SPA origin (Section 10).
2. **Rate-limit filter** — coarse request-rate guard per IP/user (Section 13).
3. **JWT authentication filter** — parses the access-token cookie, verifies signature/expiry/`tokenVersion`, populates `SecurityContext`; on failure, request proceeds unauthenticated (not blocked here) so that public endpoints (login, register, health) still work — actual enforcement happens at the route-matcher/method-security layers.
4. **Exception-translation filter** — ensures an exception thrown inside the filter chain itself (rare, but e.g., a malformed Authorization header) still returns the standard `ErrorResponse` shape (Section 12 of [02-Architecture.md](02-Architecture.md#12-exception-flow--strategy)), not a raw servlet-container error page.
5. *(Interceptor, post-dispatch)* **Correlation-id interceptor** — assigns/propagates the `traceId` used in logging (Section 13 of [02-Architecture.md](02-Architecture.md#13-logging-flow--strategy)).

---

## 8. Method Security

`@EnableMethodSecurity` is active application-wide. Every Service interface method that mutates or reads role-restricted/ownership-restricted data carries an explicit `@PreAuthorize` — there is no method that relies solely on "the controller above it happened to check." This redundancy is intentional (ADR-0004): a Service method is safe to call from *any* future caller (a new controller, a scheduled job, a future GraphQL resolver — Section 19 of [02-Architecture.md](02-Architecture.md)) without re-deriving its authorization rules at each new call site.

---

## 9. CSRF

Because authentication uses a cookie (`HttpOnly` access-token cookie, Section 3), CSRF is a relevant threat (unlike a pure `Authorization: Bearer` header scheme, which is naturally CSRF-immune). Mitigations, layered:

- **`SameSite=None`** on both the access and refresh token cookies (revised from `Strict` per the SDR-002 amendment, since the frontend and backend are deployed on different subdomains — a genuinely cross-site relationship where `Strict`/`Lax` cookies are never attached). CSRF defense here now rests entirely on the double-submit token below, not on `SameSite` enforcement.
- **CSRF token (double-submit)** for state-changing (`POST`/`PUT`/`PATCH`/`DELETE`) requests as defense in depth, since `SameSite` support/behavior can vary by browser/proxy configuration: the SPA reads a non-`HttpOnly` CSRF token cookie set at login and echoes it in a custom request header (`X-CSRF-Token`); Spring Security validates the header matches the cookie. A cross-site request cannot read the cookie to construct the matching header.
- **State-changing operations are never triggered by a plain `GET`** (no state change hides behind a link/image tag).

---

## 10. CORS

`prod`/`test`: strict allow-list of exactly the deployed SPA origin(s), credentials allowed (needed for cookie-based auth), all other origins rejected by the CORS filter (Section 7) before reaching any controller. `dev`: allow-list includes the local Vite dev server origin only — never a wildcard (`*`) in any profile, since wildcard origins are incompatible with credentialed requests and would defeat the cookie-based auth model entirely.

---

## 11. Input Validation & Output Encoding

- **Input validation:** Section 14 of [02-Architecture.md](02-Architecture.md#14-validation-strategy) (four-layer strategy) is the authoritative reference — Bean Validation rejects malformed input before it reaches business logic, which is both a correctness and a security control (the primary defense against injection-shaped payloads reaching a query).
- **SQL injection:** Structurally prevented — all data access goes through Spring Data JPA (parameterized queries / `Specification` predicates, Section 16 of [02-Architecture.md](02-Architecture.md#16-design-patterns-used)); no method in the codebase concatenates a raw SQL string with user input. Native `@Query` usage (if ever needed for a reporting query, Section 4.7 of [02-Architecture.md](02-Architecture.md)) is always parameterized (`:param` binding), never string-built.
- **XSS / output encoding:** The API returns JSON exclusively (Content-Type enforced, never reflecting user input as `text/html`); the React SPA's default JSX rendering HTML-escapes all interpolated content, and the codebase avoids `dangerouslySetInnerHTML` (a lint rule flags any use for manual security review — the only legitimate case would be a future rich-text comment renderer, which is out of current scope). Combined, there is no code path where unescaped user input becomes executable markup.
- **Mass assignment:** Structurally prevented by ADR-0009 — request DTOs declare exactly the fields a client may set; a client cannot smuggle a `role` or `status` field into a request body and have it bind to a protected entity field, because no such field exists on the request DTO for those endpoints.

---

## 12. File Upload Security

Flow diagram: [02-Architecture.md §11](02-Architecture.md#11-file-upload-flow). Concrete controls:

- **Type allow-list** (not deny-list): JPG, PNG, PDF, DOC/DOCX, XLS/XLSX, ZIP only (FR-ATT-2) — checked by inspecting actual file content/magic bytes, not just the client-supplied `Content-Type` header or filename extension (both are trivially spoofable).
- **Size limits:** Per-file and per-ticket/comment attachment-count caps (FR-ATT-3), enforced both at the multipart-parsing layer (`spring.servlet.multipart.max-file-size`, rejects oversized uploads before they're fully buffered — a DoS control) and at the Bean Validation layer, both reading from the single `FileUploadProperties` source (Section 17 of [02-Architecture.md](02-Architecture.md#17-configuration-strategy)).
- **Filename handling:** The client-supplied filename is stored as metadata only (for display/download purposes) and is never used to construct a filesystem path — the physical storage key is a generated UUID (ADR-0008), which eliminates path-traversal (`../../etc/passwd`-style) attacks by construction.
- **No execution:** Uploaded files are never stored in, or served from, a location the web server would execute as code (SRS §8 explicit requirement) — served only via an authenticated controller endpoint that streams bytes with a `Content-Disposition: attachment` header, never as a static asset with an inferred content type that a browser might execute.
- **Authorization on retrieval:** Every download re-checks ticket-visibility authorization (Section 6) at request time — an attachment's storage key alone is not a bearer credential; guessing/enumerating a key without the right role/ownership still yields 403 (FR-ATT-4, Acceptance Criterion 6).

---

## 13. Rate Limiting & Account Lockout

- **Login throttling (FR-AUTH-8):** Failed-attempt counter per account, incremented on each bad password; after 5 consecutive failures, the account is locked for 15 minutes (returned as a plain-language error, not a generic 401) — resets on successful login or after the lockout window elapses. Counter and lockout state live on the `User` row (`failed_attempts`, `locked_until`), checked before password comparison so a locked account never even reaches the BCrypt comparison (also a minor timing/DoS mitigation, since BCrypt is deliberately expensive).
- **IP-level rate limiting:** The rate-limit filter (Section 7) applies a coarser per-IP request budget on authentication endpoints specifically, mitigating distributed credential-stuffing attempts that spread across many accounts (which the per-account lockout alone would not catch).
- **General API rate limiting:** Not required at current single-organization internal scale (SRS §12), but the same filter is the designed extension point if abuse patterns emerge — an additive configuration change, not new architecture.

---

## 14. Data Protection (At-Rest / In-Transit)

- **In transit:** HTTPS/TLS is mandatory in every non-`dev` profile — the `Secure` cookie flag (Section 3) is meaningless without it, and CORS/CSRF protections assume a TLS channel an attacker cannot passively observe.
- **At rest:** Password hashes (Section 4) are the only credential-equivalent data stored, and are hashed, not merely encrypted — no key exists whose compromise would reveal a password. Database-level encryption-at-rest (disk/volume encryption) is an infrastructure-layer control expected from the hosting platform (SRS §15 AWS roadmap item — RDS encryption-at-rest is a configuration flag at that point, not an application change).
- **PII minimization:** DTOs expose only the fields a given view legitimately needs (ADR-0009) — e.g., a ticket list response includes the assigned engineer's display name, never their email or internal id, unless the viewer's role and the specific view justify it.

---

## 15. Security Logging & Auditing

Covered in full in [02-Architecture.md §13](02-Architecture.md#13-logging-flow--strategy); security-specific rules restated here for completeness:

- Authentication events (login success/failure, lockout, logout, password reset requested/completed) are logged to a dedicated logger, distinct from general application logs, to support security review without noise.
- **Never logged, at any level:** raw passwords, password hashes, raw JWTs/refresh tokens, full credit-card-equivalent data (not applicable to this system's data model, but stated as a standing rule for any future payment-adjacent feature).
- The **administrative audit log** ([02-Architecture.md §4.10](02-Architecture.md#410-audit)) separately records every user/role/category management action taken by an Administrator — who did what to which user/role/category, when — satisfying SRS §17.6's governance/compliance-readiness recommendation as a first-class part of this design rather than a deferred afterthought.

---

## 16. Dependency & Configuration Hardening

- No secret ever committed to source control (Section 17 of [02-Architecture.md](02-Architecture.md#17-configuration-strategy)) — enforced by a pre-commit secret-scanning hook as part of the CI/CD roadmap item (Section 21 of [02-Architecture.md](02-Architecture.md#21-future-architecture-roadmap)).
- Actuator endpoints (health/metrics, Section 21 of [02-Architecture.md](02-Architecture.md)) are exposed only on an internal management port / behind the same authentication boundary in `prod` — never publicly reachable with full detail (a public `/actuator/env` is a well-known real-world leak vector).
- Dependency versions are pinned and kept current against known-CVE advisories (standard Maven dependency-check tooling) as part of the CI pipeline — a process control, not an architectural one, but named here since it's part of the overall security posture.

---

## 17. Mapping to Non-Functional Requirements

| SRS §8 requirement | Satisfied by |
|---|---|
| "Passwords shall be hashed" | Section 4 |
| "all authenticated routes shall enforce role-based access control" | Sections 5–6, ADR-0004 |
| "file uploads shall be validated by type/size and never executed" | Section 12 |
| "session tokens shall expire" | Section 3 |
| Acceptance Criterion 1 (role separation enforced, not just hidden) | Sections 5–6 (two-layer enforcement, testable independently of the UI) |
| Acceptance Criterion 6 (attachment retrievable only by authorized viewers) | Section 12 |
| Acceptance Criterion 10 (plain-language errors, no internal detail exposed) | [02-Architecture.md §12](02-Architecture.md#12-exception-flow--strategy) |
