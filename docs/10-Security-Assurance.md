# 10 — Enterprise Security Architecture Document (SecAD) — Part IV

## HelpDesk Management System — Security Assurance & Roadmap

| | |
|---|---|
| **Document Version** | 1.0 |
| **Status** | Accepted — Security Architecture Phase |
| **Part of** | [07-Security-Architecture.md](07-Security-Architecture.md) (Part I) · [08-Security-Controls.md](08-Security-Controls.md) (Part II) · [09-Security-Operations.md](09-Security-Operations.md) (Part III) · Part IV (this document) |

---

## Table of Contents (this part)

19. Security Testing Strategy
20. OWASP Top 10 Compliance Matrix
21. Future Security Roadmap
22. Security Decision Records

---

## 19. Security Testing Strategy

Extends [06-Testing.md §5](06-Testing.md#5-security-tests) with the full recommended security-testing program for this system.

### 19.1 Authentication Testing
- Registration: duplicate email rejected, weak password rejected, role field ignored/absent from request DTO regardless of what a client attempts to submit (threat #18).
- Login: correct credentials succeed; wrong password/unknown email both return an identical generic `401` with equalized timing (Section 3.2 of [07-Security-Architecture.md](07-Security-Architecture.md#32-login-flow-fr-auth-3)); unverified account blocked with the specific actionable message; locked account returns `423` even with correct credentials.
- Token lifecycle: expired access token rejected; tampered-signature token rejected; `tokenVersion` mismatch (post-password-change) rejected; refresh rotation issues a new pair and invalidates the old; **refresh-token reuse is detected** (replaying an already-rotated refresh token revokes the entire token family) — this specific test is the direct verification of Section 5.3 of [07-Security-Architecture.md](07-Security-Architecture.md#53-session-renewal)'s core security property.
- Account lockout: 5th failure locks the account; a correct-password 6th attempt during the lockout window still fails; lockout clears after the window elapses.

### 19.2 Authorization Testing
- Full RBAC matrix ([07-Security-Architecture.md §7](07-Security-Architecture.md#7-permission-matrix)) exercised as a parameterized grid: every {role} × {endpoint} × {expected outcome} combination.
- BOLA/IDOR-specific: a `USER` token requesting another user's ticket by ID returns `404` (not `403`, Section 18.2 of [09-Security-Operations.md](09-Security-Operations.md#182-error-response-structure)); a `SUPPORT_ENGINEER` token requesting a ticket not assigned to them returns `404`.
- BFLA-specific: every `/admin/**` and `/reports/**` route rejects `USER` and `SUPPORT_ENGINEER` tokens with `403` at both the URL-filter layer and (isolated unit test) the method-security layer independently, verifying the two-layer redundancy (ADR-0004) actually holds — a test that disables one layer and confirms the other still catches the violation is explicitly recommended, proving the "defense in depth" claim rather than assuming it.
- Method-security self-invocation trap (Section 8.6 of [08-Security-Controls.md](08-Security-Controls.md#86-method-security)): a targeted test confirming no Service method bypasses its own `@PreAuthorize` via an internal `this`-call.

### 19.3 Penetration Testing
Recommended cadence: before the first production release, and annually (or after any major authentication/authorization-affecting change) thereafter. Scope should explicitly include: the full OWASP Testing Guide methodology (19.4), authenticated testing from each of the three roles (not just an anonymous/external perspective — internal-threat and privilege-escalation scenarios are a primary concern for this system per the threat model, Section 2 of [07-Security-Architecture.md](07-Security-Architecture.md#2-threat-model)), and explicit BOLA/BFLA-focused testing given this system's resource-ownership-heavy data model. A third-party/independent tester is recommended over internal-only testing once the system handles real organizational data, to avoid the blind-spot risk of the same team that built the controls being the only one verifying them.

### 19.4 OWASP Testing
Automated dependency/vulnerability scanning (OWASP Dependency-Check or equivalent, Section 16 of [02-Architecture.md](02-Architecture.md#16-dependency--configuration-hardening)) as a CI gate on every build; OWASP ZAP (or equivalent DAST tool) run against a deployed `test`-profile instance as part of the release pipeline, configured to authenticate as each role and crawl accordingly — a purely-anonymous DAST scan would miss the majority of this system's authorization-dependent surface.

### 19.5 Session Testing
Cookie flags verified present (`HttpOnly`, `Secure`, `SameSite=None`) on every auth-related `Set-Cookie` response in every non-`dev` profile; session (token) expiry timing verified against the configured TTLs; concurrent-session behavior verified (two logins from "different devices" both remain valid until one is explicitly revoked); forced-global-logout (`tokenVersion` bump) verified to invalidate every other outstanding session.

### 19.6 File Upload Testing
Allow-listed types accepted; disallowed types rejected regardless of a spoofed `Content-Type`/extension (a `.exe` renamed to `.pdf` must still be rejected by magic-byte inspection, Section 11.8 of [08-Security-Controls.md](08-Security-Controls.md#118-content-type-validation)); oversized files rejected; the **EICAR test file** (the industry-standard, harmless antivirus-test string) is used to verify the virus-scan integration actually rejects a detected-malicious upload end-to-end, in the `test` profile (Section 17.6 of [09-Security-Operations.md](09-Security-Operations.md#176-profiles)); path-traversal-shaped filenames (`../../etc/passwd`) verified to have zero effect on stored location; download authorization re-verified per Section 11.7 of [08-Security-Controls.md](08-Security-Controls.md#117-download-protection).

### 19.7 SQL Injection Testing
Automated SAST/linting for string-concatenated query construction (Section 13.3 of [08-Security-Controls.md](08-Security-Controls.md#133-sql-injection-prevention)) as a CI gate; targeted DAST payloads against every search/filter parameter (`GET /tickets?q=...`, category/status filters) confirming no behavioral difference (error, timing, or result-set change) indicative of injection.

### 19.8 XSS Testing
Stored-XSS payloads submitted via ticket title/description/comment content, verified to render as inert text (never executed) both via direct API-response inspection (confirms JSON-only output, Section 10 of [08-Security-Controls.md](08-Security-Controls.md#10-output-encoding-strategy)) and via the E2E browser-driven suite ([06-Testing.md §6](06-Testing.md#6-end-to-end-e2e-tests)), which is the only layer that can confirm the frontend's rendering path is also safe, not just the API contract. CSP violation-reporting (Section 13.11 of [08-Security-Controls.md](08-Security-Controls.md#1311-content-security-policy)) monitored during this testing phase specifically to catch any legitimate application behavior the policy would inadvertently block, before enforcement mode ships.

### 19.9 CSRF Testing
A state-changing request replayed with a valid session cookie but a missing/mismatched CSRF header confirmed to be rejected (`403`) before reaching business logic; a same-origin request with the correct header confirmed to succeed — both directions matter (a CSRF defense that also accidentally blocks legitimate same-origin requests is a functional regression, not just a security gap).

### 19.10 Privilege Escalation Testing
Directly targets threat #2: a `USER` token attempting every conceivable path to elevated capability — submitting a `role` field on profile-update requests (confirmed ignored, Section 9.2 of [08-Security-Controls.md](08-Security-Controls.md#92-dto-validation-bean-validation)), calling admin/engineer-scoped endpoints directly by URL, attempting to modify another user's ticket by ID manipulation, attempting to self-assign a ticket when self-assignment is disabled. This suite is run explicitly against the Permission Matrix ([07-Security-Architecture.md §7](07-Security-Architecture.md#7-permission-matrix)) as its checklist, so "have we tested privilege escalation" is answerable by a coverage count against that table, not a subjective judgment.

### 19.11 Traceability
Every test in 19.1–19.10 is tagged (e.g., `@Tag("SEC-AUTHN")`, `@Tag("SEC-BOLA")`) and mapped back to the relevant SRS Acceptance Criterion ([06-Testing.md §9](06-Testing.md#9-coverage-expectations)) and threat-model row (Section 2 of [07-Security-Architecture.md](07-Security-Architecture.md#2-threat-model)) — "is this specific threat actually tested" is a CI test-report query, not a document assertion.

---

## 20. OWASP Top 10 Compliance Matrix

Mapped against **OWASP Top 10:2021**.

| # | Category | How this architecture mitigates it |
|---|---|---|
| **A01** | **Broken Access Control** | Two-layer RBAC (ADR-0004; [07-Security-Architecture.md §4](07-Security-Architecture.md#4-authorization-architecture)); deny-by-default filter chain (Section 8.1 of [08-Security-Controls.md](08-Security-Controls.md#81-authentication-filter-chain)); ownership-aware `PermissionEvaluator`s closing BOLA/IDOR (threat #22); `404`-not-`403` on invisible resources; full permission matrix ([07-Security-Architecture.md §7](07-Security-Architecture.md#7-permission-matrix)) as the enforced, tested source of truth. |
| **A02** | **Cryptographic Failures** | BCrypt password hashing, never reversible encryption or plaintext (Section 6 of [07-Security-Architecture.md](07-Security-Architecture.md#6-password-policy), SDR-001); TLS mandatory in transit for both client↔API and API↔database (Section 13.10 of [08-Security-Controls.md](08-Security-Controls.md#1310-https-enforcement), Section 14.2 of [09-Security-Operations.md](09-Security-Operations.md#142-connection-security)); no secret ever in source control (Section 17.1 of [09-Security-Operations.md](09-Security-Operations.md#171-environment-variables--secret-management)); at-rest encryption expected from the hosting platform (Section 14.5 of [09-Security-Operations.md](09-Security-Operations.md#145-encryption-requirements)). |
| **A03** | **Injection** | 100% parameterized data access (Section 13.3 of [08-Security-Controls.md](08-Security-Controls.md#133-sql-injection-prevention)); no shell/command execution with user input (Section 13.4 of [08-Security-Controls.md](08-Security-Controls.md#134-command-injection-prevention)); allow-list, not deny-list, validation throughout (Section 9 of [08-Security-Controls.md](08-Security-Controls.md#9-input-validation-strategy)). |
| **A04** | **Insecure Design** | This entire SecAD *is* the mitigation: threat modeling performed before implementation (Section 2 of [07-Security-Architecture.md](07-Security-Architecture.md#2-threat-model)), secure-by-default and defense-in-depth stated as first-class goals (Section 1 of [07-Security-Architecture.md](07-Security-Architecture.md#1-security-goals)), every significant control backed by a reviewed decision record (Section 22), business-logic-abuse considered explicitly (threat #20) rather than only technical vulnerabilities. |
| **A05** | **Security Misconfiguration** | Profile-matrix discipline (Section 17.6 of [09-Security-Operations.md](09-Security-Operations.md#176-profiles)) with `prod` as the fail-safe default, not `dev`'s permissive posture; production hardening checklist (Section 17.7 of [09-Security-Operations.md](09-Security-Operations.md#177-production-configuration-hardening-checklist)); Swagger UI and Actuator detail closed/gated in `prod`; full security-header baseline (Section 13.8 of [08-Security-Controls.md](08-Security-Controls.md#138-http-header-strategy)) applied by default, not opt-in. |
| **A06** | **Vulnerable and Outdated Components** | Pinned, CVE-scanned dependency versions as a CI gate (Section 16 of [02-Architecture.md](02-Architecture.md#16-dependency--configuration-hardening), Section 19.4 above); minimal dependency surface by design (e.g., no unnecessary templating engine, since the API is JSON-only, Section 10 of [08-Security-Controls.md](08-Security-Controls.md#10-output-encoding-strategy)). |
| **A07** | **Identification and Authentication Failures** | Strong password policy (Section 6 of [07-Security-Architecture.md](07-Security-Architecture.md#6-password-policy)); account lockout (Section 3.9 of [07-Security-Architecture.md](07-Security-Architecture.md#39-account-locking-fr-auth-8)); short-lived, rotated, reuse-detected tokens (Section 3/5 of [07-Security-Architecture.md](07-Security-Architecture.md#3-authentication-architecture)); no session-fixation vector by design (Section 5.5 of [07-Security-Architecture.md](07-Security-Architecture.md#55-session-fixation-protection)); MFA named as an explicit near-term roadmap item (Section 21). |
| **A08** | **Software and Data Integrity Failures** | Append-only, tamper-resistant audit trails (ADR-0006, Section 15 of [09-Security-Operations.md](09-Security-Operations.md#15-audit-strategy)); optimistic locking preventing silent lost-update integrity issues (ADR-0010); CI-gated dependency integrity checks (A06); signed JWTs (forgery-resistant by construction, Section 3 of [07-Security-Architecture.md](07-Security-Architecture.md#3-authentication-architecture)). |
| **A09** | **Security Logging and Monitoring Failures** | Comprehensive, structured security logging (Section 16 of [09-Security-Operations.md](09-Security-Operations.md#16-logging-strategy)) with `traceId` correlation; dual audit streams (Section 15 of [09-Security-Operations.md](09-Security-Operations.md#15-audit-strategy)); every authZ failure, CSRF failure, and file-upload violation explicitly logged as a security event, not just a generic error — designed to be SIEM/alerting-ready (Section 21) rather than log-and-forget. |
| **A10** | **Server-Side Request Forgery (SSRF)** | No current feature in [01-SRS.md](01-SRS.md)'s scope accepts a URL and has the server fetch it (no webhook/URL-preview/remote-import feature exists) — the primary mitigation is the absence of the vector. If a future feature introduces one (e.g., a webhook-notification delivery target, plausible under the Kafka/RabbitMQ or third-party-integration roadmap items), the mandated design at that time is a strict destination allow-list and a network-level egress restriction preventing the application server from reaching internal/metadata-service addresses (e.g., `169.254.169.254`) — recorded here as a standing constraint on that future work. |

This matrix is reviewed and re-verified at the same cadence as the penetration-testing schedule (Section 19.3) and whenever a new OWASP Top 10 revision is published.

---

## 21. Future Security Roadmap

Every item below is designed to be **additive** against the architecture in Parts I–III — none require revisiting the core authentication/authorization model, only extending it, per the Future-Proof security goal ([07-Security-Architecture.md §1](07-Security-Architecture.md#1-security-goals)).

| Future capability | How this architecture already accommodates it | Integration shape |
|---|---|---|
| **JWT** | Already the day-one mechanism (ADR-0003) — not a future item, listed here only for completeness against the prompt's checklist. | — |
| **OAuth2 / Google Login / Microsoft Login** | `AuthenticationService` is already an interface ([02-Architecture.md §4.1](02-Architecture.md#41-authentication)); an OAuth2/OIDC-based implementation issues the same internal JWT contract (Section 3 of [07-Security-Architecture.md](07-Security-Architecture.md#3-authentication-architecture)) after federated verification. | New `AuthenticationService` implementation + Spring Security's OAuth2 Client support; zero change to downstream authorization (Section 4), since every module only ever sees a validated principal, never the auth method used to obtain it. |
| **MFA (generic) / TOTP** | The login flow (Section 3.2 of [07-Security-Architecture.md](07-Security-Architecture.md#32-login-flow-fr-auth-3)) already has a clean insertion point between "password verified" and "token issued" — a second-factor challenge step slots in there without restructuring the surrounding flow. | Add an `mfa_enabled`/`mfa_secret` (encrypted) field to `User`; login becomes a two-step exchange (password → short-lived MFA-pending token → TOTP code → full token pair); recommended as the definitive mitigation for credential-stuffing (threat #14) once introduced, and strongly recommended to be mandatory for the `ADMIN` role first given its elevated blast radius (Section 4.3 of [07-Security-Architecture.md](07-Security-Architecture.md#43-role-admin)). |
| **WebAuthn / Passkeys** | Same insertion point as MFA above; WebAuthn's challenge-response model replaces (or supplements) the TOTP step without affecting token issuance downstream. | New credential type stored per-user (public key, not a shared secret — itself a security improvement over TOTP's shared-secret model); standard library support (e.g., Spring Security's WebAuthn support) integrates at the authentication-provider layer only. |
| **SSO (generic, SAML/OIDC)** | Same `AuthenticationService` abstraction point as OAuth2 above. | An SSO-specific implementation; role mapping from the IdP's asserted group/claim to this system's fixed three roles (Section 4 of [07-Security-Architecture.md](07-Security-Architecture.md#4-authorization-architecture)) is the main design decision at that time — the `Role` entity ([05-Database.md §3](05-Database.md#3-relationship-justification-cardinality-explained)) already being a table, not a hardcoded enum, makes this mapping a configuration/data concern, not a schema change. |
| **LDAP / Active Directory** | Spring Security's LDAP authentication provider is a drop-in `AuthenticationProvider` alternative to the local BCrypt-password check (Section 3.2 of [07-Security-Architecture.md](07-Security-Architecture.md#32-login-flow-fr-auth-3)) — the rest of the token-issuance/RBAC pipeline is unaffected. | Most relevant if this system is ever adopted by an organization with existing centralized directory infrastructure; local-password accounts and directory-backed accounts can coexist by user record (a `authProvider` discriminator column). |
| **API Gateway** | The API is already versioned (ADR-0012) and stateless (ADR-0003) — both prerequisites for sitting behind a gateway that terminates TLS, enforces coarse rate limiting, and routes to the backend without the backend needing to change how it authenticates a request (the gateway forwards the same JWT cookie/header untouched). | Infrastructure addition; the application-level rate limiting (Section 12.5 of [08-Security-Controls.md](08-Security-Controls.md#125-rate-limiting)) remains as defense-in-depth even after a gateway adds its own layer, not replaced by it. |
| **Reverse Proxy** | TLS termination and HSTS (Section 13.8/13.10 of [08-Security-Controls.md](08-Security-Controls.md#138-http-header-strategy)) are already designed to be reverse-proxy-compatible (the application trusts `X-Forwarded-*` headers only from a configured, trusted proxy hop — never from an arbitrary client, preventing a spoofed-origin-IP bypass of the per-IP rate limiting in Section 12.5). | Standard reverse-proxy deployment (nginx/ALB); no application redesign. |
| **Redis Session Store** | Not required by the current stateless design (ADR-0003) — named here because SRS §15 lists it explicitly. Its natural application in this architecture is as the **cache** backing `@Cacheable` reference data ([02-Architecture.md §18](02-Architecture.md#18-performance-considerations)), and/or as a distributed store for rate-limiting counters (Section 12.5 of [08-Security-Controls.md](08-Security-Controls.md#125-rate-limiting)) once the application runs as multiple horizontally-scaled instances (a per-instance in-memory rate-limit counter stops being globally accurate at that point — Redis is the designed upgrade path for exactly that moment, not before). | Dependency + configuration addition; does not become a session-of-record store, since authentication remains stateless/JWT-based (ADR-0003) even after Redis is introduced for these other purposes. |

---

## 22. Security Decision Records

Every significant security decision referenced throughout Parts I–III is recorded as an individual **Security Decision Record (SDR)** under [SDR/](SDR/), indexed in [Security-Decisions.md](Security-Decisions.md). SDRs follow the same discipline as the architecture ADRs ([Decisions.md](Decisions.md)) — Decision / Reason / Alternatives Considered / Pros / Cons / Future Impact — but are scoped specifically to security-relevant choices, some of which extend an existing architecture ADR (e.g., SDR-002 extends ADR-0003) rather than duplicating it.

| SDR | Title |
|---|---|
| [SDR-001](SDR/SDR-001-bcrypt-password-hashing.md) | BCrypt Password Hashing with Cost Factor 12 |
| [SDR-002](SDR/SDR-002-httponly-cookie-token-delivery.md) | HttpOnly/Secure/SameSite Cookie Delivery for JWT (extends ADR-0003) |
| [SDR-003](SDR/SDR-003-refresh-token-rotation-reuse-detection.md) | Refresh Token Rotation with Reuse Detection |
| [SDR-004](SDR/SDR-004-two-layer-rbac-enforcement.md) | Two-Layer RBAC Enforcement (extends ADR-0004) |
| [SDR-005](SDR/SDR-005-account-lockout-policy.md) | Account Lockout Policy (5 Attempts / 15-Minute Window) |
| [SDR-006](SDR/SDR-006-remember-me-via-refresh-token.md) | "Remember Me" via Sliding Refresh Token, Not a Separate Mechanism |
| [SDR-007](SDR/SDR-007-double-submit-csrf-protection.md) | Double-Submit CSRF Protection over Server-Side Synchronizer Tokens |
| [SDR-008](SDR/SDR-008-strict-content-security-policy.md) | Strict Content Security Policy with Report-Only Rollout |
| [SDR-009](SDR/SDR-009-generic-error-responses.md) | Generic, Catalog-Based Error Messages (No Exception-Detail Passthrough) |
| [SDR-010](SDR/SDR-010-file-upload-defense-in-depth.md) | File Upload Defense in Depth (Magic-Byte Validation + Virus Scanning) |
| [SDR-011](SDR/SDR-011-database-role-separation.md) | Database Role Separation (App / Migrator / Read-Only) |
| [SDR-012](SDR/SDR-012-dual-audit-log-streams.md) | Dual Audit Log Streams — Ticket Activity vs. Administrative/Security (extends ADR-0006) |
| [SDR-013](SDR/SDR-013-layered-rate-limiting.md) | Layered Rate Limiting (Global IP, Auth-Specific, Per-User) |
| [SDR-014](SDR/SDR-014-security-header-baseline.md) | Security Header Baseline Applied to Every Response |
| [SDR-015](SDR/SDR-015-secret-management-progressive-hardening.md) | Progressive Secret Management (Env Vars Now, Managed Secret Store Later) |
| [SDR-016](SDR/SDR-016-no-forced-password-expiration.md) | No Forced Periodic Password Expiration |
| [SDR-017](SDR/SDR-017-pluggable-authentication-for-future-mfa-sso.md) | Pluggable Authentication Strategy for Future MFA/OAuth2/SSO/LDAP |

See [Security-Decisions.md](Security-Decisions.md) for the maintained index (status/date) and [SDR/](SDR/) for full record content.
