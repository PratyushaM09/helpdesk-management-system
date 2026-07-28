# 07 — Enterprise Security Architecture Document (SecAD) — Part I

## HelpDesk Management System

| | |
|---|---|
| **Document Version** | 1.0 |
| **Status** | Accepted — Security Architecture Phase |
| **Source of Truth (unmodified)** | [01-SRS.md](01-SRS.md), [02-Architecture.md](02-Architecture.md) |
| **Supersedes (as the authoritative security design)** | [03-Security.md](03-Security.md) — retained as the original architecture-phase security summary; this SecAD set (07–10 + [SDR/](SDR/)) is the detailed, governing security design going forward |
| **Companion Parts** | [08-Security-Controls.md](08-Security-Controls.md) · [09-Security-Operations.md](09-Security-Operations.md) · [10-Security-Assurance.md](10-Security-Assurance.md) · [SDR/](SDR/) indexed in [Security-Decisions.md](Security-Decisions.md) |

This document set is the **Security Architecture Document (SecAD)**. It governs *how* every requirement in [01-SRS.md](01-SRS.md) and every component in [02-Architecture.md](02-Architecture.md) must be secured. No implementation may deviate from it without a new, reviewed Security Decision Record (SDR — Section 22 of this SecAD, in [10-Security-Assurance.md](10-Security-Assurance.md#22-security-decision-records)).

**Role naming note:** [01-SRS.md](01-SRS.md) and [03-Security.md](03-Security.md) name the three roles `USER`, `SUPPORT_ENGINEER`, `ADMIN`. This SecAD uses the shorthand `SUPPORT` interchangeably with `SUPPORT_ENGINEER` for table brevity only — they are the same role; the persisted/coded role identifier remains `SUPPORT_ENGINEER` ([05-Database.md §1](05-Database.md#1-entity-inventory)).

---

## Table of Contents (this part)

1. Security Goals
2. Threat Model
3. Authentication Architecture
4. Authorization Architecture
5. Session Management
6. Password Policy
7. Permission Matrix

*(Sections 8–22 continue in [08-Security-Controls.md](08-Security-Controls.md), [09-Security-Operations.md](09-Security-Operations.md), and [10-Security-Assurance.md](10-Security-Assurance.md).)*

---

## 1. Security Goals

| Goal | What it means concretely in this system | Justification |
|---|---|---|
| **Secure by Default** | Every new endpoint is unauthenticated-denied and unauthorized-denied until explicitly opted into a role; every new DTO field is unexposed until explicitly mapped (ADR-0009); every new entity association is `LAZY` until explicitly fetched. | The cost of an insecure default is a silent vulnerability that ships unnoticed; the cost of a secure default is a build/test failure that ships loudly. SRS Acceptance Criterion 1 requires role separation to be *enforced*, not incidentally true. |
| **Least Privilege** | Each role (Section 4) can perform only the actions its function requires — a Support Engineer cannot manage users; a User cannot triage; the database application user cannot `DROP TABLE` (Section 14 of [09-Security-Operations.md](09-Security-Operations.md#14-database-security)). | Limits blast radius: a compromised low-privilege session (e.g., a phished User account) cannot escalate to administrative capability by itself. |
| **Defense in Depth** | Every critical control exists at more than one layer — authorization at URL *and* method level (ADR-0004), input validation at DTO *and* business *and* database level ([02-Architecture.md §14](02-Architecture.md#14-validation-strategy)), file-type validation at MIME-header *and* content-sniffing level (Section 11 of [08-Security-Controls.md](08-Security-Controls.md#11-file-upload-security)). | A single missed check (a forgotten annotation, a bypassed client) is not a full breach if a second, independent layer still catches it. |
| **Zero Trust (inspired)** | No request is trusted by network origin or prior authentication state alone — every request re-validates the JWT signature/expiry/version (Section 3), and every service-layer method independently re-checks resource ownership (Section 4) rather than trusting that "the controller already checked." Internal service-to-service calls (future microservices roadmap) are designed to carry the same principal context forward, not rely on network-perimeter trust. | The system has no assumed-safe internal network in its threat model (SRS §12 explicitly excludes infrastructure-level trust boundaries from this phase, but the *application* is designed not to depend on one existing). |
| **OWASP Top 10 Aligned** | Every current OWASP Top 10 (2021) category is explicitly mapped to a concrete control (Section 20 of [10-Security-Assurance.md](10-Security-Assurance.md#20-owasp-top-10-compliance-matrix)). | Provides an auditable, industry-recognized baseline rather than an ad hoc security posture. |
| **Least Astonishment / Maintainable** | Security logic is centralized (one `security` package, [02-Architecture.md §5](02-Architecture.md#5-package-structure)), never duplicated per-module, and every security-relevant constant (role names, permission strings) has exactly one source of truth ([09-Security-Operations.md](09-Security-Operations.md)). | A security control that is easy to find is a security control that gets maintained correctly as the system grows (SRS §8 Maintainability). |
| **Extensible / Future-Proof** | Every current mechanism (JWT auth, RBAC, password auth) is built behind an interface/abstraction so that MFA, OAuth2/SSO, LDAP, or a Redis session store (Section 21 of [10-Security-Assurance.md](10-Security-Assurance.md#21-future-security-roadmap)) can be added without redesigning what already ships. | Directly required by SRS §15 (future integrations must not be architecturally blocked) and by this SecAD's mandate to be future-proof. |
| **Auditable** | Every authentication event, authorization failure, and administrative action is logged in a way that reconstructs "who did what, when, from where" without ambiguity (Section 15–16 of [09-Security-Operations.md](09-Security-Operations.md)). | SRS §8 Auditability; a security control that can't be verified after the fact is not a control an incident responder can rely on. |

---

## 2. Threat Model

**Scope and method:** this is an asset-and-attacker-centric threat model over the system described in [02-Architecture.md](02-Architecture.md) — a single-organization, internal-facing helpdesk web application (SRS §12) with three roles, ticket/comment/attachment data, and a REST API consumed by a first-party SPA (ADR-0002). Likelihood ratings assume the deployment context SRS §12 defines (internal org tool, not a public internet consumer product) — they should be re-assessed if the system is ever exposed to anonymous public registration or a higher-value data set. Every threat below maps forward to the specific control section that mitigates it.

| # | Threat | Risk (attack scenario) | Likelihood | Impact | Primary Mitigation |
|---|---|---|---|---|---|
| 1 | **Unauthorized Access** | An unauthenticated actor reaches an endpoint that should require login (e.g., a missed route in the filter chain). | Low | High | Default-deny filter chain (Section 8 of [08-Security-Controls.md](08-Security-Controls.md#8-spring-security-design)); every route classified explicit-public or authenticated, no implicit-public gap. |
| 2 | **Privilege Escalation** | A `USER` crafts a request to act as `ADMIN` (e.g., editing their own `role` field, or calling an admin endpoint directly). | Medium | Critical | Role is never a client-settable DTO field (ADR-0009, mass-assignment immunity, threat #18); two-layer RBAC (ADR-0004, Section 4); `tokenVersion` invalidation on role change (Section 3 of [03-Security.md](03-Security.md#3-session-management)). |
| 3 | **Broken Authentication** | Weak password policy, unthrottled login, or predictable session tokens let an attacker guess/forge a valid session. | Medium | Critical | Password policy (Section 6); BCrypt (SDR-001); account lockout (SDR-005); signed, short-lived JWT with rotation (Section 3). |
| 4 | **Broken Access Control** | A valid, authenticated `USER` reaches data/functions belonging to another user or role because an authorization check is missing on one specific path. | Medium | High | Defense-in-depth RBAC (ADR-0004) enforced identically regardless of entry point (Zero Trust goal, Section 1); security test suite parameterized over the full permission matrix ([06-Testing.md §5](06-Testing.md#5-security-tests)). |
| 5 | **Session Hijacking** | An attacker obtains a valid session token (network sniffing, XSS, shared device) and replays it. | Low | High | `HttpOnly`/`Secure`/`SameSite=None` cookies (Section 5); mandatory HTTPS/TLS (Section 13 of [08-Security-Controls.md](08-Security-Controls.md#13-application-security)); short access-token TTL (15 min) limits the exploitation window. |
| 6 | **CSRF** | A malicious site tricks an authenticated user's browser into submitting a state-changing request using their live session cookie. | Medium | Medium | Double-submit CSRF token on all state-changing requests (Section 9 of [03-Security.md](03-Security.md#9-csrf), extended in SDR-007); `SameSite` no longer contributes here since it was revised to `None` (SDR-002 amendment). |
| 7 | **XSS (Reflected/Stored/DOM)** | Attacker injects a `<script>` payload via a ticket title/description/comment that executes in another user's browser. | Medium | High | Output is JSON-only from the API (never `text/html`); React's default JSX escaping; no `dangerouslySetInnerHTML` (lint-enforced); strict CSP (Section 13 of [08-Security-Controls.md](08-Security-Controls.md#13-application-security), SDR-008). |
| 8 | **SQL Injection** | Attacker embeds SQL syntax in a search/filter field hoping it reaches a concatenated query. | Very Low | Critical | 100% parameterized access via Spring Data JPA/Hibernate and `Specification` predicates ([03-Security.md §11](03-Security.md#11-input-validation--output-encoding)); no string-concatenated queries anywhere in the codebase (enforced by code review checklist, Section 19 of [10-Security-Assurance.md](10-Security-Assurance.md#19-security-testing-strategy)). |
| 9 | **Command Injection** | Attacker crafts input reaching a shell/OS command (e.g., a filename passed to an external virus-scan process). | Low | Critical | No user input is ever passed to a shell/`Runtime.exec` call; the one plausible vector (virus-scan invocation, Section 11 of [08-Security-Controls.md](08-Security-Controls.md#11-file-upload-security)) uses a library/daemon-socket integration (e.g., ClamAV over its daemon protocol), never a spawned shell command with interpolated input. |
| 10 | **File Upload Attacks** (malware, polyglot files, executable disguised as document) | Attacker uploads a malicious file as a ticket/comment attachment hoping it is later opened/executed by another user or the server. | Medium | High | Type allow-list + magic-byte content sniffing, size caps, non-executable storage, antivirus scan hook, randomized storage keys (Section 11 of [08-Security-Controls.md](08-Security-Controls.md#11-file-upload-security)). |
| 11 | **Path Traversal** | Attacker manipulates a filename (`../../etc/passwd`) hoping to read/write outside the intended storage directory. | Low | High | Client filename is metadata only, never used to construct a filesystem path; physical storage key is a generated UUID (ADR-0008, Section 11 of [08-Security-Controls.md](08-Security-Controls.md#11-file-upload-security)). |
| 12 | **Sensitive Data Exposure** | Password hashes, internal IDs, or another user's PII leak through an over-broad API response or verbose error message. | Medium | High | DTO allow-list mapping (ADR-0009) — a field is invisible until deliberately exposed; generic error messages (Section 18 of [09-Security-Operations.md](09-Security-Operations.md#18-error-handling)); TLS in transit (Section 13 of [08-Security-Controls.md](08-Security-Controls.md#13-application-security)). |
| 13 | **Brute Force** | Automated repeated login attempts against one account to guess its password. | Medium | High | Per-account lockout after 5 failures (SDR-005); BCrypt's inherent computational cost slows guessing; IP-level rate limiting on `/auth/**` (Section 12 of [08-Security-Controls.md](08-Security-Controls.md#12-api-security)). |
| 14 | **Credential Stuffing** | Attacker replays breached email/password pairs from other services against this system's login. | Medium | High | Rate limiting is IP- *and* account-aware (catches distributed low-and-slow attempts, SDR-013); strong password policy reduces password reuse viability; future roadmap: breach-database check on registration/reset (Section 21 of [10-Security-Assurance.md](10-Security-Assurance.md#21-future-security-roadmap)), MFA as the definitive mitigation once added. |
| 15 | **Clickjacking** | Application is framed inside a malicious page, tricking a user into clicking a disguised action (e.g., "close ticket"). | Low | Medium | `X-Frame-Options: DENY` and CSP `frame-ancestors 'none'` (Section 13 of [08-Security-Controls.md](08-Security-Controls.md#13-application-security)). |
| 16 | **Replay Attacks** | A captured valid request (e.g., a token or a state-changing API call) is resent later to repeat its effect. | Low | Medium | Short-lived access tokens; refresh-token rotation with reuse detection (Section 3 of [03-Security.md](03-Security.md#3-session-management)); idempotent design on state-transition endpoints where feasible ([04-API-Design.md §1](04-API-Design.md#1-conventions-applied-to-every-endpoint)) so an accidental/malicious replay of a transition request is rejected as an illegal transition, not silently re-applied. |
| 17 | **Session Fixation** | Attacker pre-sets a victim's session identifier before login, then hijacks the now-authenticated session. | Low | High | Stateless JWT model issues a brand-new token pair only *after* successful credential verification — there is no pre-authentication session identifier for an attacker to fix (Section 5). |
| 18 | **Mass Assignment** | Attacker adds unexpected fields (`role`, `status`, `assignedEngineerId`) to a request body hoping they bind to protected entity fields. | Medium | Critical | Request DTOs declare only client-settable fields; MapStruct mappers are allow-list, not reflection-based (ADR-0009) — an unlisted field is structurally ignored, not merely validated away. |
| 19 | **Parameter Tampering** | Attacker modifies a hidden/client-side value (price-equivalent: a `ticketId`, `priority`, or `page size` parameter) to access or affect data beyond intent. | Medium | Medium | Every parameter is re-validated and re-authorized server-side regardless of client-side UI constraints (Zero Trust goal); `size` query param is capped server-side (max 100, [04-API-Design.md §1](04-API-Design.md#1-conventions-applied-to-every-endpoint)) so it cannot be tampered into an unbounded/DoS-shaped request. |
| 20 | **Business Logic Abuse** | Attacker exploits a legitimate feature in an unintended sequence (e.g., repeatedly reopening/closing a ticket to spam notifications, or resolving a ticket that was never assigned to bypass accountability). | Medium | Medium | Workflow-transition Strategy validator is the single, exhaustively-tested source of truth for legal state transitions (FR-FLOW-1, [06-Testing.md §2](06-Testing.md#2-unit-tests)); reopen window and rate-limited actions bound abuse of legitimate flows. |
| 21 | **Denial of Service** | Attacker floods an endpoint, uploads oversized files repeatedly, or requests unbounded result sets to exhaust server resources. | Medium | Medium | Mandatory pagination (FR-PAGE-1); file size/count caps (Section 11 of [08-Security-Controls.md](08-Security-Controls.md#11-file-upload-security)); rate limiting (Section 12 of [08-Security-Controls.md](08-Security-Controls.md#12-api-security)); connection pool sizing ([02-Architecture.md §18](02-Architecture.md#18-performance-considerations)). Full DDoS-scale mitigation is an infrastructure/CDN concern out of this phase's scope (SRS §12), noted in the roadmap. |
| 22 | **Broken Object Level Authorization (BOLA/IDOR)** | A `USER` changes `/tickets/{id}` to another user's ticket ID and retrieves it because only role, not ownership, was checked. | Medium | High | Every resource-scoped endpoint re-checks ownership via `PermissionEvaluator` at the Service layer (ADR-0004, Section 6 of [03-Security.md](03-Security.md#6-authorization-mechanics)); a non-visible ticket returns `404`, never `403`, to avoid confirming existence (Section 4). |
| 23 | **Broken Function Level Authorization (BFLA)** | A `SUPPORT_ENGINEER` calls an `ADMIN`-only endpoint (e.g., category deletion) directly, bypassing UI hiding. | Low | High | URL-level role gating + method-level `@PreAuthorize` on every admin function (ADR-0004); security tests assert every admin route rejects non-admin tokens ([06-Testing.md §5](06-Testing.md#5-security-tests)). |
| 24 | **Race Conditions** | Two concurrent requests (e.g., simultaneous reassignment and status change) interleave and leave the ticket in an inconsistent state, or a double-submit creates two identical tickets. | Medium | Medium | Optimistic locking (`@Version`, ADR-0010) on `Ticket`; append-only activity log makes any interleaving visible/auditable even if timing is unlucky (ADR-0006); idempotency conventions ([04-API-Design.md §1](04-API-Design.md#1-conventions-applied-to-every-endpoint)). |
| 25 | **Improper Exception Handling** | An unhandled exception leaks a stack trace, SQL fragment, or internal class name to the client, aiding further attack. | Medium | Medium | Single `GlobalExceptionHandler` is the only place an exception becomes an HTTP response ([02-Architecture.md §12](02-Architecture.md#12-exception-flow--strategy)); full detail logged server-side only, generic message + `traceId` returned to client (Section 18 of [09-Security-Operations.md](09-Security-Operations.md#18-error-handling)). |
| 26 | **Information Disclosure** | Verbose responses, predictable sequential IDs, or timing differences reveal whether a resource/account exists to an unauthorized caller. | Medium | Medium | `404` (not `403`) for invisible resources (Section 4); identical response timing/shape for "account doesn't exist" vs "wrong password" and for `forgot-password`/`resend-verification` (always `202`, [04-API-Design.md §2](04-API-Design.md#2-authentication-module)); surrogate `BIGINT` IDs are not treated as secret, but never used as a sole access-control credential (attachment `storageKey` is opaque but authorization is always re-checked, [03-Security.md §12](03-Security.md#12-file-upload-security)). |

---

## 3. Authentication Architecture

Foundational mechanism: stateless JWT access token + rotating opaque refresh token (ADR-0003), delivered via `HttpOnly`/`Secure`/`SameSite=None` cookies (Section 5). This section walks every flow step by step; component interaction diagrams already exist in [02-Architecture.md §8](02-Architecture.md#8-authentication-flow) and are not redrawn here.

### 3.1 Registration Flow (FR-AUTH-1)
1. Client submits `{name, email, password}` to `POST /auth/register` (public endpoint).
2. Structural validation (Bean Validation): email format, password meets policy (Section 6), name non-empty.
3. Business validation: email not already registered (case-insensitive uniqueness check against `user.email`).
4. Password is hashed with BCrypt (Section 6) — the plaintext password is never persisted, logged, or held in memory longer than the hashing call requires.
5. `User` row created with `role = USER` (hardcoded server-side — the request DTO has no `role` field at all, closing threat #2/#18 structurally, not just by validation) and `account_status = UNVERIFIED`.
6. A signed, time-limited (24h) email-verification token is generated and delivered via the notification abstraction (ADR-0007; in-app now, email-ready).
7. Response: `201` with the new user's id/email/status — **never** the password hash, **never** an auto-issued session (registration does not equal login; see Section 3.2).

### 3.2 Login Flow (FR-AUTH-3)
1. Client submits `{email, password}` to `POST /auth/login`.
2. Server looks up the user by email. If not found, the flow still executes a dummy BCrypt comparison against a fixed hash before returning a generic `401` — this equalizes response timing between "no such account" and "wrong password," closing the account-enumeration side channel (threat #26).
3. If found: check `account_status` — `UNVERIFIED` → specific actionable error ("please verify your email"); `LOCKED`/lockout window active → `423` (Section 3.9).
4. BCrypt-compare the submitted password against the stored hash (constant-time by construction of the algorithm).
5. On failure: increment `failed_attempts`; if it crosses the threshold, set `locked_until` (Section 3.9); log the failure to the authentication logger (Section 16 of [09-Security-Operations.md](09-Security-Operations.md#16-logging-strategy)); return generic `401`.
6. On success: reset `failed_attempts` to zero; issue a new access token (15 min, JWT) and refresh token (7 days, opaque, hashed and stored server-side); set both as `HttpOnly`/`Secure`/`SameSite=None` cookies; also issue the CSRF token cookie (Section 9 of [03-Security.md](03-Security.md#9-csrf)) needed for subsequent state-changing calls.
7. Log the successful login (user id, timestamp, source IP, **never** the password) to the authentication logger.
8. Response: `200` with a minimal body (`userId`, `role`, `name`) — the tokens travel only as cookies, never in the JSON body (keeps them out of browser history, JS-accessible memory, and application logs that might record response bodies).

### 3.3 Logout Flow (FR-AUTH-4)
1. Client calls `POST /auth/logout` (authenticated).
2. Server resolves the refresh token from the cookie and marks its server-side record `revoked_at = now()` — this is what makes logout an actual security event, not just a client-side cookie deletion (a stolen refresh token captured before logout is now unusable).
3. Both cookies are cleared (`Set-Cookie` with immediate expiry).
4. The just-issued access token remains cryptographically valid until its own natural 15-minute expiry — an accepted, documented tradeoff (ADR-0003) given the short TTL bounds the exposure window.
5. Logout event logged.

### 3.4 Forgot Password Flow (FR-AUTH-5)
1. Client submits `{email}` to `POST /auth/forgot-password`.
2. Regardless of whether the email exists, the endpoint returns `202` after the same processing time (Section 3.2's enumeration-resistance principle applied here too).
3. If the account exists, a signed, single-use, 1-hour-expiry reset token is generated and delivered via the notification channel.
4. Rate-limited per email/IP (Section 12 of [08-Security-Controls.md](08-Security-Controls.md#12-api-security)) to prevent using this endpoint as a notification-spam or enumeration-timing vector.

### 3.5 Reset Password Flow (FR-AUTH-5)
1. Client submits `{token, newPassword}` to `POST /auth/reset-password`.
2. Token is verified: signature valid, not expired, not already used (single-use — consumed atomically on success).
3. New password validated against policy (Section 6).
4. Password hash updated; `tokenVersion` incremented (Section 3.9) — this immediately invalidates every access token currently outstanding for this user, and every refresh token is revoked (a compromised-password scenario must not leave old sessions alive anywhere).
5. Event logged; user is required to log in fresh (no auto-login after reset, consistent with the registration principle in 3.1).

### 3.6 Password Change Flow (FR-AUTH-6)
1. Authenticated client submits `{currentPassword, newPassword}` to `POST /auth/change-password`.
2. Current password is re-verified via BCrypt-compare **even though the caller already holds a valid session** — this specifically defeats the scenario where a session is hijacked/left unattended but the attacker doesn't know the password: they cannot pivot to changing it either.
3. New password validated against policy and against password history (Section 6).
4. Hash updated; `tokenVersion` incremented (all other sessions/devices are logged out — a deliberate "security-first" default, since an unexpected password change is itself a signal worth forcing re-authentication everywhere).
5. Event logged.

### 3.7 Remember Me
No traditional "Remember Me" persistent-auto-login checkbox is implemented as a *separate* mechanism. The refresh-token model (7-day sliding expiry, rotated on every use, Section 5) already provides "stay logged in across browser restarts for up to a week without re-entering a password" — the behavior users expect from "Remember Me" — without the security weaknesses of a classic long-lived remember-me cookie (which historically bypasses MFA and often uses weaker validation than the primary auth path). See SDR-006 for the full reasoning and the rejected alternative (a separate, longer-lived remember-me token).

### 3.8 Email Verification (FR-AUTH-2)
1. User follows the verification link/enters the token from Section 3.1 step 6.
2. `POST /auth/verify-email {token}`: signature and expiry checked; on success, `account_status` moves `UNVERIFIED → VERIFIED`.
3. Login (Section 3.2) is refused for any account not in `VERIFIED` status — this is a hard gate, not a soft warning, ensuring FR-AUTH-2's "shall require email verification before full account access" is actually enforced, not merely recommended.
4. A resend-verification endpoint exists (`POST /auth/resend-verification`), rate-limited identically to forgot-password (Section 3.4), for the case where the original token expired or the email was lost.

### 3.9 Account Locking (FR-AUTH-8)
- Threshold: 5 consecutive failed login attempts.
- Lockout duration: 15 minutes, sliding — a successful login before the window elapses is impossible by definition (the check happens before password comparison, Section 3.2 step 3), and the counter only resets on an eventual successful login, not merely on window expiry (a 6th attempt after the window simply gets one more chance, not a full reset, preventing an attacker from probing exactly up to the threshold repeatedly forever without ever being logged in).
- An Administrator can manually unlock an account (`PATCH /admin/users/{id}/status` equivalent action) — logged as an administrative action (Section 15 of [09-Security-Operations.md](09-Security-Operations.md#15-audit-strategy)).
- Lockout state and reason are surfaced to the user in plain language ("Too many failed attempts — try again in a few minutes") without revealing the exact remaining wait time (minor information-disclosure hardening) or whether the account exists at all if the email itself was wrong (Section 3.2).

### 3.10 Password Expiration
**Not implemented — deliberate decision.** Forced periodic password rotation (e.g., "change every 90 days") is not part of this design. See SDR-016 for the full justification: current NIST 800-63B guidance and OWASP's Authentication Cheat Sheet both recommend *against* mandatory periodic rotation for user-chosen passwords, since it empirically drives predictable password patterns (`Password1`, `Password2`, …) and increases help-desk burden without a demonstrated security benefit, when weighed against strong hashing (Section 6), breach-driven forced resets (below), and the future MFA roadmap (Section 21 of [10-Security-Assurance.md](10-Security-Assurance.md#21-future-security-roadmap)) as the more effective controls. Forced reset **is** triggered event-drivenly: on suspected compromise (Administrator action), and implicitly via `tokenVersion` invalidation after any password change (Section 3.6).

### 3.11 Session Timeout
Access token: 15-minute absolute expiry, non-renewable itself (a new one must be minted via refresh). Refresh token: 7-day absolute expiry from issuance, extended by rotation only while actively used (each successful refresh issues a new 7-day-window token) — full detail in Section 5.

### 3.12 Concurrent Session Handling
Multiple concurrent sessions (different devices/browsers) are **permitted by default** — each login issues its own independent refresh-token record (Section 5 of [05-Database.md](05-Database.md#5-cascade-rules), `User 1:N RefreshToken`). This matches the internal-helpdesk usage pattern (a Support Engineer legitimately open on both a desktop and a laptop) rather than a high-security single-session banking model. An Administrator-triggered "log out everywhere" (Section 3.6's `tokenVersion` mechanism, also available as a standalone admin action) is the control available when concurrent sessions must be forcibly terminated (e.g., a reported stolen device) — see Section 5.6 for the full policy and SDR list for the alternative (strict single-session enforcement) considered and rejected.

---

## 4. Authorization Architecture

RBAC with three fixed roles (Assumption A1 of [01-SRS.md](01-SRS.md#13-assumptions)), enforced through the two-layer model established in ADR-0004 and [03-Security.md §5–6](03-Security.md#5-rbac-matrix). This section restates and extends that design with the depth this SecAD phase requires.

### 4.1 Role: USER

| | |
|---|---|
| **Permissions** | Register/verify/login/logout own account; manage own profile and password; create tickets; view/comment/attach on own tickets; close own resolved tickets; reopen own closed tickets within the reopen window; view own notifications. |
| **Restricted Actions** | Cannot view, comment on, or modify any ticket they did not create; cannot change ticket status/priority/category/assignment (triage actions); cannot access any `/admin/**` or `/reports/**` route; cannot see other users' profile details beyond what a ticket view legitimately exposes (e.g., assigned engineer's display name only). |
| **Module Access** | Authentication, Profile, Tickets (own only), Comments (own tickets only), Attachments (own tickets only), Notifications (own only), User Dashboard. |
| **Data Visibility** | Row-level: `ticket.created_by = self` only. Never sees another user's ticket, even in search results (FR-SRCH-2). |
| **Future Extensibility** | A future "team" or "department" grouping (not in current SRS scope) could extend visibility to "own team's tickets" via an additional `PermissionEvaluator` predicate — additive, no change to the `USER` role's core contract. |

### 4.2 Role: SUPPORT (SUPPORT_ENGINEER)

| | |
|---|---|
| **Permissions** | Everything a `USER` can do on tickets **assigned to them**: view, comment, attach, change status/priority/category (within legal workflow transitions, FR-FLOW-1/2), escalate, self-assign (where enabled). View their own workload/performance on the Engineer Dashboard. |
| **Restricted Actions** | Cannot view/act on tickets not assigned to them (no blanket "see all tickets" access — this is deliberately narrower than a naive "staff role sees everything" default); cannot assign tickets to *other* engineers (Administrator-only, FR-TICK-8); cannot manage users, roles, or categories; cannot soft-delete a ticket; cannot access `/admin/**` or the admin audit log. |
| **Module Access** | Everything `USER` has (on their own account/profile) plus: Tickets (assigned only, full workflow actions), Comments/Attachments (assigned tickets), Engineer Dashboard. No Reports or Administration access. |
| **Data Visibility** | Row-level: `ticket.assigned_engineer_id = self` only. Cannot browse the full ticket backlog — this is an explicit design choice (Section 4.2 rationale below) reinforcing accountability (SRS Business Objective B3) by keeping "what am I responsible for" unambiguous. |
| **Future Extensibility** | A "team lead" tier (sees teammates' tickets read-only) is a plausible future role addition — because the RBAC model is already table-driven at the `Role` entity level ([05-Database.md §3](05-Database.md#3-relationship-justification-cardinality-explained)), adding it is a new row + new `PermissionEvaluator` predicate, not a redesign. |

**Design rationale — why Support Engineers don't see the full ticket list:** an alternative design (all engineers see all open tickets, self-serve pick their work) was considered and rejected for this phase. SRS FR-TICK-8 defines assignment as an Administrator-driven (or explicitly-enabled self-assignment) action specifically to keep workload distribution deliberate (Persona "Meera," SRS §5) rather than emergent from engineers cherry-picking easy tickets — a known real-world helpdesk failure mode. Self-assignment, where enabled, is still scoped to *unassigned* (`OPEN`) tickets only, not a general "browse everything" capability.

### 4.3 Role: ADMIN

| | |
|---|---|
| **Permissions** | Full CRUD-equivalent capability across Users (create engineer/admin accounts, change roles, activate/deactivate, unlock), Tickets (view/assign/reassign/soft-delete any ticket), Categories (create/rename/deactivate), Reports (generate/export all report types), Audit Log (view). |
| **Restricted Actions** | Cannot hard-delete a ticket (only soft-delete, ADR-0005 — even Administrators are bound by the audit-integrity design, a deliberate self-restriction); cannot demote/deactivate the last remaining Administrator account (a business-continuity guard, [04-API-Design.md §3](04-API-Design.md#3-user--profile-module)); cannot view another user's raw password (impossible by design — hashes are one-way, Section 6). |
| **Module Access** | All modules, including Administration and Audit — the only role with access to `/admin/**` and `/reports/**`. |
| **Data Visibility** | Full visibility across all tickets, users, and reports — the only role without row-level ownership scoping. Every Administrator action against another user's data is itself logged to the admin audit stream (Section 15 of [09-Security-Operations.md](09-Security-Operations.md#15-audit-strategy)), so "full visibility" is paired with "full accountability," not unmonitored access. |
| **Future Extensibility** | A narrower "Report Viewer" or "User Manager" sub-role (splitting today's monolithic Admin capability) is a natural future refinement if the organization grows past a single admin persona — the permission-constant model (Section 4.4) already expresses permissions independently of role, so splitting `ADMIN` into finer roles is a matter of redistributing existing permission constants across new role rows, not inventing new checks. |

### 4.4 Method-Level Authorization Strategy

**Recommendation: Spring Security method security (`@PreAuthorize`) with SpEL expressions backed by custom `PermissionEvaluator` beans, evaluated at the Service layer — never solely at the Controller layer.** This is the concrete mechanism implementing ADR-0004's two-layer model:

- **Layer 1 (coarse, URL-based):** `SecurityFilterChain` route matchers gate entire path prefixes by role (`hasRole('ADMIN')`, `hasAnyRole('SUPPORT_ENGINEER','ADMIN')`). Fast, fails before any business code runs (also a DoS-resistance property, threat #21).
- **Layer 2 (fine, ownership-aware):** `@PreAuthorize` annotations on **Service interface methods** (not `impl`, so the contract is visible where the interface is read — Section 3 of [02-Architecture.md](02-Architecture.md#3-low-level-architecture-layer-responsibilities)), expressed as SpEL calling into named `PermissionEvaluator` beans: e.g. `@PreAuthorize("hasRole('ADMIN') or @ticketPermissionEvaluator.canView(#ticketId, authentication)")`.
- **Permission constants, not string literals:** role names and permission identifiers are referenced from the single `constants` package ([02-Architecture.md §5](02-Architecture.md#5-package-structure)) — a SpEL expression never hardcodes `"ADMIN"` as a bare string duplicated across files; it references the shared constant, so a rename or a new role is a one-place change.
- **Why Service layer, not Controller layer, for the fine-grained check:** a `@PreAuthorize` on a Controller method only protects that one HTTP entry point; the same rule placed on the Service method protects *every* caller of that method, present and future (a scheduled job, a future GraphQL resolver, an internal call from another module) — directly the Zero Trust goal (Section 1) and consistent with [02-Architecture.md §3](02-Architecture.md#3-low-level-architecture-layer-responsibilities)'s "business rules live only in Services" principle.
- **`PermissionEvaluator` cost discipline:** each evaluator loads only the minimal ownership columns needed (e.g., `ticket.created_by`, `ticket.assigned_engineer_id`) via a lightweight projection query, never the full entity graph, keeping the authorization check cheap even under load.
- **Deny-by-default:** any Service method touching role- or ownership-scoped data that is *missing* a `@PreAuthorize` annotation is treated as a code-review-blocking defect, not an oversight to fix later — enforced via the security test suite's route/method inventory check ([06-Testing.md §5](06-Testing.md#5-security-tests)) and a recommended static-analysis rule (e.g., an ArchUnit test asserting every public method in an `impl` package touching a `@PreAuthorize`-eligible entity carries the annotation).

---

## 5. Session Management

### 5.1 Session Creation
No server-side session object is created (ADR-0003 — stateless architecture). "Session" in this system means the *token pair* (access + refresh) issued at successful login (Section 3.2) or successful refresh (5.3). Creation always follows full credential verification — never provisional/partial.

### 5.2 Session Expiration
- **Access token:** 15 minutes, absolute, non-extendable — expiry is enforced purely by JWT `exp` claim verification on every request; no server-side state is consulted for this check (stateless by design), which is precisely what keeps the auth filter cheap under load.
- **Refresh token:** 7 days, absolute from issuance, but effectively sliding under normal use because each use rotates it (5.3) — a user active at least once a week never re-authenticates; a dormant session expires within a week of last use.

### 5.3 Session Renewal
`POST /auth/refresh` is called transparently by the SPA (an HTTP interceptor) whenever an API call fails with `401` due to access-token expiry. The refresh token (cookie-delivered, never touched by JavaScript) is validated (not expired, not revoked, hash matches a live record), and **rotated**: a new access token *and* a new refresh token are issued together, the old refresh token record is marked consumed/replaced (`replaced_by_id`, [05-Database.md §2](05-Database.md#2-entity-relationship-diagram)), and both new cookies are set. Rotation is the core session-renewal security property: it turns a stolen-but-unused refresh token into a self-revoking liability for the attacker the moment the legitimate user's next refresh happens (Section 5.5).

### 5.4 Session Invalidation
Three independent invalidation paths, all logged (Section 15 of [09-Security-Operations.md](09-Security-Operations.md#15-audit-strategy)):
1. **Explicit logout** (Section 3.3) — revokes one refresh token (the current device/session only).
2. **`tokenVersion` increment** (password change/reset, Administrator-forced logout) — invalidates *every* outstanding access token for the user on their next request, and revokes *all* refresh tokens — a "logout everywhere" event.
3. **Natural expiry** — access token stops verifying after 15 minutes; refresh token stops being accepted after 7 days of non-use, with no explicit action required.

### 5.5 Session Fixation Protection
Structurally not applicable in the classic sense (there is no pre-authentication session identifier to fix, Section 2 threat #17) — but the equivalent modern risk (a stale/pre-issued token being reused post-privilege-change) is closed by: tokens are minted fresh only after credential verification (never reused across the unauthenticated→authenticated boundary), and refresh-token rotation (5.3) ensures a token captured before a privilege change (e.g., a role promotion) does not silently carry forward — the `tokenVersion` claim (bumped on any Administrator role change, Section 4.3) forces re-issuance with the new role.

### 5.6 Concurrent Login Policy
Default: **permitted**, unlimited-by-role concurrent sessions (Section 3.12) — appropriate for this system's internal, trusted-workforce context (SRS §12). This is a deliberate, documented choice, not an omission — see SDR list for the alternative (strict single-session, forcibly logging out any prior session on new login) and why it was rejected for this phase (poor fit for legitimate multi-device use, and the "logout everywhere" capability already exists for the genuine incident-response case, 5.4 item 2). A future per-role concurrent-session cap (e.g., limiting `ADMIN` sessions specifically, given their elevated blast radius) is a configuration-level change against the existing `RefreshToken` table, not a redesign.

### 5.7 Remember Me Strategy
See Section 3.7 — implemented as the standard 7-day rotating refresh token, not a separate longer-lived mechanism. No "remember me" checkbox grants materially different security properties than the default session; this avoids the OWASP-documented anti-pattern of a remember-me path that quietly bypasses controls (e.g., MFA, once added — Section 21 of [10-Security-Assurance.md](10-Security-Assurance.md#21-future-security-roadmap)) that the primary login path enforces.

### 5.8 Cookie Security

| Cookie | Flags | Purpose |
|---|---|---|
| `access_token` | `HttpOnly`, `Secure`, `SameSite=None`, `Path=/api`, no `Max-Age` beyond token TTL | Carries the JWT; inaccessible to JavaScript (closes XSS-token-theft, threat #7/#5). |
| `refresh_token` | `HttpOnly`, `Secure`, `SameSite=None`, `Path=/api/v1/auth/refresh` (scoped narrowly — sent only to the one endpoint that needs it, minimizing exposure on every other request) | Carries the opaque refresh token. |
| `csrf_token` | `Secure`, `SameSite=None`, **not** `HttpOnly` (must be readable by the SPA's JS to echo into the `X-CSRF-Token` header, Section 9 of [03-Security.md](03-Security.md#9-csrf)) | Double-submit CSRF defense. |

`Secure` is non-negotiable in every non-`dev` profile (Section 13 of [08-Security-Controls.md](08-Security-Controls.md#13-application-security) — HTTPS enforcement); in `dev`, `Secure` may be relaxed only for `http://localhost` per the standard browser same-origin exception, never in any deployed environment.

`SameSite=None` (revised from the original `Strict`, see [SDR-002](SDR/SDR-002-httponly-cookie-token-delivery.md) amendment): the frontend and backend are deployed on different subdomains (e.g. separate Render services), which browsers treat as different sites. `SameSite=Strict`/`Lax` cookies are never attached to cross-site requests, so with the original flag every authenticated request past login failed with `401` despite the cookies being correctly stored. `SameSite=None` requires `Secure` (already mandatory here) and is only usable over HTTPS. This removes `SameSite` as a redundant second CSRF layer — CSRF defense now rests solely on the double-submit token (threat #6, SDR-007), which was always the primary, independent control and remains fully intact.

---

## 6. Password Policy

| Rule | Value | Why |
|---|---|---|
| **Minimum length** | 10 characters | Length is the single strongest determinant of brute-force resistance (OWASP ASVS); 10 is chosen over the historical "8" minimum to meaningfully raise the search-space floor without imposing unreasonable friction on a general workforce (Personas, SRS §5). |
| **Complexity** | At least one uppercase, one lowercase, one digit, one symbol | Combined with length, broadens the character set exponentially; enforced via the `@StrongPassword` custom Bean Validation constraint ([02-Architecture.md §14](02-Architecture.md#14-validation-strategy)), evaluated identically at registration, reset, and change (Section 3) so there is exactly one policy definition, not three drifting copies. |
| **Hashing algorithm** | BCrypt, cost factor 12 in `prod` (SDR-001) | Purpose-built, adaptive-cost password hash (unlike a general-purpose fast hash such as SHA-256, which is exactly what makes it *unsuitable* for passwords) — cost factor 12 is tuned to be expensive enough to resist offline brute-force at current hardware capability while remaining fast enough for interactive login latency; the cost factor is a config value, not a hardcoded literal, so it can be raised over time as hardware improves without a data migration (BCrypt hashes embed their own cost factor, so old and new-cost hashes coexist and verify correctly during a gradual re-hash-on-login rollout). |
| **Password history** | Last 5 hashes retained, reuse rejected | Prevents the common evasion of "change password" policies by cycling `Password1`→`Password2`→back to `Password1`; only *hashes* are retained (never plaintext or reversible encryption) — history checking is a BCrypt-compare against each retained hash, same as login. |
| **Reset rules** | Single-use, 1-hour-expiry signed token; invalidates all sessions on completion (Section 3.5) | Bounds the exploitation window of an intercepted reset link and ensures a reset genuinely re-secures the account rather than leaving old sessions live. |
| **Storage policy** | Only the current BCrypt hash + the last 5 historical hashes are stored; plaintext password never touches the database, logs (Section 16 of [09-Security-Operations.md](09-Security-Operations.md#16-logging-strategy)), or any DTO (ADR-0009) at any point after the initial in-memory hashing step. | The storage policy is what makes a full database compromise a costly-to-crack-offline event rather than an instant full account-takeover event — the entire point of hashing over encryption for this data class. |
| **No forced periodic rotation** | — | See Section 3.10 / SDR-016. |

---

## 7. Permission Matrix

Full feature-by-role capability matrix. `R`=Read, `W`=Write (create), `U`=Update, `D`=Delete, `Ap`=Approve/Confirm-equivalent action, `As`=Assign, `Mg`=Manage (full lifecycle incl. config), `Ex`=Export. A blank cell means the role has no access to that action for that feature at all (not merely "denied at runtime" — the UI must not offer it either, though UI hiding is never the enforcement mechanism, Section 4.4).

| Feature | Action | USER | SUPPORT | ADMIN |
|---|---|---|---|---|
| **Own Profile** | R / U | R, U (own) | R, U (own) | R, U (own) |
| **Other Users** | R / Mg | — | — | R, Mg |
| **User Roles** | U | — | — | U |
| **Ticket** | W (create) | W | — | — |
| **Ticket** | R | R (own) | R (assigned) | R (all) |
| **Ticket** | U (edit desc/attachments, pre-triage) | U (own, pre-triage states) | — | U (all) |
| **Ticket** | U (status/priority/category — triage) | — | U (assigned) | U (all) |
| **Ticket** | As (assign/reassign) | — | As (self, where enabled) | As (any) |
| **Ticket** | Ap (close) | Ap (own) | — | Ap (all) |
| **Ticket** | U (reopen) | U (own, within window) | — | U (all) |
| **Ticket** | D (soft delete) | — | — | D |
| **Ticket** | Ex (export list) | Ex (own results) | Ex (assigned results) | Ex (all results) |
| **Ticket Activity/Timeline** | R | R (own tickets) | R (assigned tickets) | R (all) |
| **Comment** | W / R | W, R (own tickets) | W, R (assigned tickets) | W, R (all) |
| **Comment** | U (edit own, window) | U (own comment) | U (own comment) | U (own comment) |
| **Attachment** | W / R | W, R (own tickets) | W, R (assigned tickets) | W, R (all) |
| **Attachment** | D | D (own upload) | D (own upload) | D (any) |
| **Notifications** | R / U (mark read) | R, U (own) | R, U (own) | R, U (own) |
| **Category** | R | R (active only) | R (active only) | R (all, incl. inactive) |
| **Category** | W / U / Mg (deactivate) | — | — | W, U, Mg |
| **Dashboard** | R | R (User dashboard) | R (Engineer dashboard) | R (Admin dashboard) |
| **Reports** | R / Ex | — | — | R, Ex |
| **Admin Audit Log** | R | — | — | R |
| **System Configuration** | Mg | — | — | Mg |

This matrix is the single source of truth both enforcement layers (Section 4.4) implement against, and is the direct input to the parameterized RBAC security test grid ([06-Testing.md §5](06-Testing.md#5-security-tests)) — a change to this table is the trigger for a corresponding test-suite update, never the other way around.
