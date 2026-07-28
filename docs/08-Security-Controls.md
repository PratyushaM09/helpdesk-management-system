# 08 — Enterprise Security Architecture Document (SecAD) — Part II

## HelpDesk Management System — Security Controls

| | |
|---|---|
| **Document Version** | 1.0 |
| **Status** | Accepted — Security Architecture Phase |
| **Part of** | [07-Security-Architecture.md](07-Security-Architecture.md) (Part I) · Part II (this document) · [09-Security-Operations.md](09-Security-Operations.md) (Part III) · [10-Security-Assurance.md](10-Security-Assurance.md) (Part IV) |

No Java, Spring configuration, controller, or repository code appears in this document — every design below is described structurally (component responsibilities, ordering, decision logic) for implementation to follow.

---

## Table of Contents (this part)

8. Spring Security Design
9. Input Validation Strategy
10. Output Encoding Strategy
11. File Upload Security
12. API Security
13. Application Security

---

## 8. Spring Security Design

### 8.1 Authentication Filter Chain

The chain is a strictly ordered sequence of Servlet filters, each with one responsibility (single-responsibility applied to the filter chain itself, consistent with [02-Architecture.md §1](02-Architecture.md#1-architectural-principles)):

| Order | Filter | Responsibility | Rejects / Short-circuits when |
|---|---|---|---|
| 1 | **CORS filter** | Validate `Origin` against the configured allow-list (Section 10 of [03-Security.md](03-Security.md#10-cors)); handle preflight `OPTIONS`. | Origin not on the allow-list → request rejected before any further filter runs. |
| 2 | **HTTPS-enforcement / HSTS filter** | Reject (or redirect, per profile — see 13.9) plain-HTTP requests in non-`dev` profiles; attach `Strict-Transport-Security` to responses. | Non-TLS request in `prod`/`test` → rejected. |
| 3 | **Rate-limit filter** | Coarse per-IP and per-account request-rate guard, applied most strictly to `/auth/**` (Section 12.5). | Budget exceeded → `429` returned immediately, no further processing. |
| 4 | **JWT authentication filter** | Extract the `access_token` cookie; verify signature, expiry, and `tokenVersion` claim against the current user record; on success, populate the `SecurityContext` with a fully-formed `Authentication` (principal + granted authorities derived from `role`). | Missing/invalid/expired token → request proceeds **unauthenticated** (not blocked here — public endpoints like `/auth/login` must still work); the *authorization* filters below are what actually reject an unauthenticated request to a protected route. |
| 5 | **CSRF validation filter** | For state-changing methods (`POST`/`PUT`/`PATCH`/`DELETE`) on cookie-authenticated requests, validate the double-submit CSRF header against the CSRF cookie (Section 13.1). | Mismatch/missing → `403` before the request reaches authorization or business logic. |
| 6 | **Authorization (`FilterSecurityInterceptor`-equivalent)** | Evaluate the URL-level route-matcher rules (ADR-0004 Layer 1) against the now-populated `SecurityContext`. | Authenticated-but-wrong-role, or unauthenticated-on-a-protected-route → `401`/`403`. |
| 7 | **Correlation-id interceptor** *(Spring `HandlerInterceptor`, post-`DispatcherServlet`, not a Servlet filter — [02-Architecture.md §5](02-Architecture.md#5-package-structure) explains the distinction)* | Assign/propagate `traceId` into MDC for the remainder of the request's logging (Section 16 of [09-Security-Operations.md](09-Security-Operations.md#16-logging-strategy)). | Never rejects — pure observability plumbing. |

**Why this order specifically:** CORS and HTTPS/rate-limiting run first because they are cheap, network-level guards that should reject hostile traffic before any parsing/crypto work (JWT verification, however fast, is still strictly more expensive than an origin-string comparison) — a deliberate DoS-resistance ordering (threat #21). Authentication runs before CSRF validation because CSRF validation is only meaningful in the context of an authenticated, cookie-bearing request. Authorization runs last among the rejecting filters because it is the most expensive check (may consult granted authorities derived from a DB-backed role) and should only run once cheaper filters have already passed.

### 8.2 Authorization Flow

Restated from [02-Architecture.md §9](02-Architecture.md#9-authorization-flow) and [03-Security.md §6](03-Security.md#6-authorization-mechanics) as the concrete Spring Security shape: URL-level rules are declared as an ordered list of route-matcher-to-authority mappings evaluated top-to-bottom, most-specific-first (e.g., `/api/v1/admin/**` before a catch-all `/api/v1/**` requiring "any authenticated role") — ordering mistakes here (a broad rule matching before a narrow one) are the single most common real-world Spring Security misconfiguration, so the design mandates most-specific-first ordering as a reviewed, tested invariant ([06-Testing.md §5](06-Testing.md#5-security-tests) — an explicit test asserts the effective rule for every declared route prefix). Method-level (`@PreAuthorize`) authorization is described in full in [07-Security-Architecture.md §4.4](07-Security-Architecture.md#44-method-level-authorization-strategy).

### 8.3 Security Context

- Populated exactly once per request, by the JWT authentication filter (8.1, step 4) — never mutated later in the request lifecycle.
- Carries: principal (user id), granted authority (single role, `ROLE_USER`/`ROLE_SUPPORT_ENGINEER`/`ROLE_ADMIN` — Spring Security's `ROLE_` prefix convention), and the token's `tokenVersion` claim (compared against the live DB value only at authentication time, not re-checked per method call, to keep per-request DB load bounded — a deliberate freshness-vs-cost tradeoff: a role change or forced logout takes effect on the *next* token verification, i.e., within one access-token lifetime, 15 minutes maximum staleness, which is judged an acceptable window for this system's risk profile).
- Because the architecture is stateless (ADR-0003), the `SecurityContext` is request-scoped only (`SecurityContextHolder` strategy: `MODE_THREADLOCAL`, cleared at the end of every request) — never stored in an `HttpSession`, and never assumed to survive across requests.
- Propagation to async/scheduled work (e.g., a domain-event listener executing after the request's transaction commits, ADR-0007): the listener does not inherit the web request's `SecurityContext` (it may run after the response has already been sent); where a listener needs an actor identity, it reads it from the event payload (the event itself carries the acting user id, captured at publish time), never by re-reading a thread-local that may no longer be valid.

### 8.4 Custom UserDetails Design (conceptual)

The `UserDetails` a `PermissionEvaluator`/authentication provider works with is a **thin, purpose-built projection** of the `User` entity — not the entity itself (consistent with ADR-0009's DTO/entity separation applied to the security boundary too): it carries exactly `userId`, `email`, `passwordHash` (used only during the login credential-check step, never retained in the `SecurityContext` afterward), `role`, `accountStatus`, `tokenVersion`. It deliberately excludes every other `User` field (name, avatar, profile data) — the security layer has no need of them, and a leaner principal object is both cheaper to build per-request and structurally incapable of leaking unrelated profile data through a security-layer bug.

### 8.5 Session Creation Policy

Explicitly **stateless**: no `HttpSession` is created or consulted for authentication state at any point (Spring Security's stateless session-creation policy) — this is the direct architectural consequence of ADR-0003 and is what makes horizontal scaling of the application tier require no sticky-session or shared-session-store infrastructure ([02-Architecture.md §19](02-Architecture.md#19-scalability-plan)).

### 8.6 Method Security

`@EnableMethodSecurity` active application-wide, pre-post-annotation style (`@PreAuthorize`/`@PostAuthorize` where a check can only be made after loading the result — e.g., confirming a returned entity's ownership matches the caller, used sparingly since a `PermissionEvaluator` pre-check is preferred whenever the needed ownership data can be loaded cheaply up front, Section 4.4 of [07-Security-Architecture.md](07-Security-Architecture.md#44-method-level-authorization-strategy)). Method security is evaluated via AOP proxy around every Spring-managed `@Service` bean — a consequence of this is that **self-invocation** (a method calling another `@PreAuthorize`-annotated method on `this` within the same class) does not go through the proxy and would silently skip the check; the design mandates that any Service method requiring its own authorization is never called internally as a bare `this.method()` shortcut — cross-checking calls route through the injected interface reference (standard Spring AOP proxy discipline), and this rule is called out explicitly here because it is the most common way method security is accidentally defeated in real Spring codebases.

### 8.7 Filter Ordering Summary

Filter order is treated as a security-relevant configuration artifact, not an incidental default — Section 8.1's table *is* the required order, and any deviation is a reviewable, justified exception, not a routine tuning knob.

---

## 9. Input Validation Strategy

Extends [02-Architecture.md §14](02-Architecture.md#14-validation-strategy)'s four-layer model with the security-specific detail this SecAD phase requires.

### 9.1 Request Validation
Every inbound request is validated for **shape** before it is validated for **content**: content-type enforcement (a JSON endpoint rejects non-`application/json` bodies outright, `415`), request-size limits (both overall body size and, for multipart, per-part size — Section 11), and structural JSON well-formedness (a malformed body is a `400`, handled uniformly by the Global Exception Handler, never a `500`).

### 9.2 DTO Validation (Bean Validation)
As described in [02-Architecture.md §14](02-Architecture.md#14-validation-strategy): `@NotBlank`/`@Size`/`@Email`/`@Pattern`/custom constraints on every request DTO field, triggered via `@Valid`. Security-specific additions:
- **Allow-list, not deny-list, patterns** wherever a field has a constrained shape (e.g., ticket status/priority values validated against the known enum set, category id validated as existing/active) — a deny-list of "bad characters" is never used as an injection defense (it is both bypassable and the wrong layer — parameterization, Section 13.3, is the actual SQL-injection defense; Bean Validation's job here is data-integrity, not security-critical filtering).
- **Length caps on every free-text field** (ticket title, description, comment content) — not just for UX, but to bound worst-case payload size feeding into downstream processing (search indexing, notification text) and to blunt a class of resource-exhaustion input (threat #21).

### 9.3 Business Validation
Service-layer checks that require reading current data state: workflow-transition legality (FR-FLOW-1), uniqueness (email, category name), ownership (Section 4 of [07-Security-Architecture.md](07-Security-Architecture.md#4-authorization-architecture)), and cross-field consistency (e.g., a reopen request only valid within the configured window). These are always explicit `if`/exception-throwing checks in Service.impl — never encoded only as a database constraint that would surface as an opaque integrity-violation error (Section 18 of [09-Security-Operations.md](09-Security-Operations.md#18-error-handling)).

### 9.4 Database Validation
The last-resort layer (Section 14 of [02-Architecture.md](02-Architecture.md#14-validation-strategy)): `NOT NULL`, `UNIQUE`, `CHECK`, and foreign-key constraints ([05-Database.md §4](05-Database.md#4-keys--constraints)) catch anything that reaches the database despite the layers above — a deliberate defense-in-depth stance (Section 1 of [07-Security-Architecture.md](07-Security-Architecture.md#1-security-goals)), not a substitute for them (a constraint violation is a slower, coarser failure mode than an early Bean Validation rejection, and its raw error is never surfaced to the client directly — Section 18 of [09-Security-Operations.md](09-Security-Operations.md#18-error-handling)).

### 9.5 Sanitization
- **No HTML sanitization library is applied to stored ticket/comment text** — because the design's XSS defense is output-side (Section 10), not input-side stripping. Input-side HTML stripping is deliberately avoided as the *primary* defense because it is easy to get subtly wrong (encoding edge cases, mutation XSS) and because stripping content also destroys legitimate user data (a user describing an actual `<script>` tag they encountered as their support issue should not have it silently mangled). If a future rich-text comment editor is introduced (not in current scope), a dedicated allow-list HTML sanitizer (e.g., a Java port of the DOMPurify allow-list model) would be introduced at that point, applied on write, in addition to — not instead of — output encoding.
- **Filename sanitization:** the client-supplied filename is never used structurally (Section 11.6) but is still normalized for safe *display* (stripped of control characters, length-capped) before being stored as metadata.
- **Whitespace/normalization:** email addresses are lower-cased and trimmed before uniqueness checks and storage, preventing `User@x.com` and `user@x.com` from being treated as distinct accounts (a data-integrity concern that also has a minor security dimension — account-enumeration-by-case-variation).

---

## 10. Output Encoding Strategy

- **The API surface is JSON-only.** Every response's `Content-Type` is `application/json` (or `text/csv`/binary for the specific export and download endpoints, [04-API-Design.md](04-API-Design.md)) and is never permitted to reflect user input as `text/html` under any circumstance — this single rule structurally eliminates reflected-XSS-via-API-response as a vector (threat #7), because a browser only executes script from an HTML/JS-typed response.
- **Jackson (JSON serialization)** encodes all string content per the JSON spec by default (quotes, control characters escaped) — no custom serializer is permitted to bypass this for any field.
- **Frontend rendering (React/JSX):** every place ticket/comment/user-supplied text is rendered goes through JSX's default text-node interpolation (`{value}`), which HTML-escapes automatically. `dangerouslySetInnerHTML` is banned by lint rule (Section 9.5) — any future legitimate use (e.g., a rich-text renderer) requires an explicit, reviewed exception paired with the sanitizer described in 9.5.
- **CSV export encoding (FR-TICK-11, FR-REP-2):** a specific, well-known risk — "CSV injection" / "formula injection," where a cell value beginning with `=`, `+`, `-`, or `@` is interpreted as a formula by Excel/Sheets when the file is opened, potentially executing an attacker-controlled formula on the exporting Administrator's machine. Mitigation: any exported field value beginning with one of those characters is prefixed with a single leading apostrophe (or tab) before being written to the CSV, forcing spreadsheet applications to treat it as literal text — applied uniformly to every export path (ticket export, report export), not case-by-case.
- **Error messages** (Section 18 of [09-Security-Operations.md](09-Security-Operations.md#18-error-handling)) never echo raw user input back unescaped into a message that could itself be rendered somewhere unexpected — validation-error messages reference the *field name* and a static reason string, not a raw reflection of the submitted value.

---

## 11. File Upload Security

Extends [03-Security.md §12](03-Security.md#12-file-upload-security) and ADR-0008 with full policy detail.

### 11.1 Allowed Types
Images: JPG/JPEG, PNG. Documents: PDF, DOC, DOCX, XLS, XLSX. Archives: ZIP (FR-ATT-2). This is the complete allow-list — nothing outside it is accepted, regardless of extension or declared `Content-Type`.

### 11.2 Blocked Types
Everything not on the allow-list is blocked by construction (allow-list semantics, not an enumerated deny-list — Section 9.2's principle applied here). Explicitly called out as always-rejected regardless of any future allow-list expansion: any executable or script-capable format (`.exe`, `.bat`, `.sh`, `.js`, `.jar`, `.msi`, `.dll`, `.php`, `.html`/`.htm` — the latter specifically because an uploaded HTML file, if ever served with an inferred content type, could itself carry an XSS payload), and any format with a history of embedded-macro/active-content risk beyond what's explicitly allow-listed (e.g., `.docm`/`.xlsm` macro-enabled variants are *not* on the allow-list even though `.docx`/`.xlsx` are).

### 11.3 Maximum Size
Per-file cap (default 10 MB, configurable via `FileUploadProperties`, [02-Architecture.md §17](02-Architecture.md#17-configuration-strategy)) and a per-ticket/per-comment maximum attachment count (FR-ATT-3) — both enforced at two points: the multipart-parsing layer (rejects an oversized upload before it is fully buffered into memory, a DoS control, threat #21) and the Bean Validation layer (a clear, actionable message rather than a raw servlet-container error).

### 11.4 Virus Scanning Strategy
An `AttachmentService`-internal scan step, positioned **after** type/size validation and **before** the file is committed to durable storage: the uploaded byte stream is submitted to a virus-scanning engine (recommended: ClamAV, integrated via its daemon socket protocol — never a spawned shell command, closing threat #9) before the `FileStorageService.store()` call (ADR-0008) is invoked. A file failing the scan is rejected outright (`422`, generic "this file could not be accepted" message — the specific malware signature is never echoed to the client, only logged server-side, Section 16 of [09-Security-Operations.md](09-Security-Operations.md#16-logging-strategy)) and the rejection itself is logged as a security event. This scan step is designed as an interface (`AttachmentScanner`) so it can be a no-op/pass-through in `dev` (no local ClamAV dependency required for everyday development) while being mandatory and fail-closed (scan-unavailable ⇒ reject the upload, never silently skip the scan) in `prod`.

### 11.5 Storage Strategy
Per ADR-0008: behind the `FileStorageService` abstraction, local disk in this phase, S3-ready later — bytes are never stored inside the database, and never stored in any directory the web server would serve as static content or execute as code.

### 11.6 Random File Names / Path Validation
The physical storage key is always a server-generated UUID (`{ticketId}/{uuid}`), never derived from the client-supplied filename in any way — this is the structural (not merely validated) defense against path traversal (threat #11): there is no code path where user input reaches a filesystem path-construction operation, so a `../../` payload in a filename has nowhere to take effect. The original filename is retained purely as display metadata (`Attachment.original_filename`), sanitized per Section 9.5, and used only when setting the `Content-Disposition` header on download (Section 11.7) — itself header-encoded to prevent header-injection via a crafted filename.

### 11.7 Download Protection
Every attachment download re-executes the full ticket-visibility authorization check (Section 4 of [07-Security-Architecture.md](07-Security-Architecture.md#4-authorization-architecture)) at request time — the storage key is not treated as a bearer credential, and guessing/enumerating a UUID without the right role/ownership still yields `403`/`404` (FR-ATT-4, threat #22). Files are streamed through the authenticated `AttachmentController` endpoint only; there is no static-file URL that bypasses the controller (ADR-0008), and the response always carries `Content-Disposition: attachment` (never `inline`) so a browser never attempts to render/execute the downloaded content in-page, regardless of its MIME type.

### 11.8 Content-Type Validation
Client-declared `Content-Type` and filename extension are both treated as **untrusted hints only**. The authoritative type check is **magic-byte/content sniffing** (inspecting the actual file signature — e.g., the PDF `%PDF-` header, the PNG signature bytes, the ZIP local-file-header signature that DOCX/XLSX are themselves built on) performed server-side against the allow-list in Section 11.1, before any further processing — a file whose declared type and actual content disagree, or whose actual content doesn't match any allowed signature, is rejected regardless of what the client claimed.

---

## 12. API Security

### 12.1 Authentication
Every non-public endpoint requires a valid access-token cookie (Section 3 of [07-Security-Architecture.md](07-Security-Architecture.md#3-authentication-architecture)) — enforced at filter step 4/6 (Section 8.1).

### 12.2 Authorization
Two-layer RBAC (Section 4.4 of [07-Security-Architecture.md](07-Security-Architecture.md#44-method-level-authorization-strategy)) — enforced at filter step 6 and at every Service method.

### 12.3 CSRF
Double-submit token (Section 9 of [03-Security.md](03-Security.md#9-csrf); SDR-007) — enforced at filter step 5. `SameSite=None` (revised from `Strict`, SDR-002 amendment) no longer contributes CSRF resistance; the double-submit token is the sole CSRF control.

### 12.4 CORS
Strict origin allow-list, credentials permitted only for the allow-listed SPA origin, never a wildcard (Section 10 of [03-Security.md](03-Security.md#10-cors)) — enforced at filter step 1.

### 12.5 Rate Limiting
Layered, per SDR-013:
- **Global per-IP budget** on all `/api/v1/**` traffic — a coarse abuse/DoS guard.
- **Tighter per-IP-and-per-account budget on `/auth/**`** specifically (login, forgot-password, resend-verification) — the highest-value target for credential-stuffing/brute-force (threats #13/#14), so it gets the strictest limit (e.g., 10 attempts per IP per 5 minutes, independent of the per-account lockout in Section 3.9, which is a separate control targeting a different attack shape — distributed low-and-slow attempts against one account vs. one IP spraying many accounts).
- **Per-user budget on write-heavy endpoints** (ticket creation, comment creation, attachment upload) to bound business-logic-abuse-shaped flooding (threat #20/#21) without punishing normal multi-user traffic from behind a shared corporate NAT/IP.
- Response on limit exceeded: `429 Too Many Requests` with a `Retry-After` header — never a silent drop (which would look like an outage) and never a detailed explanation of the exact threshold (minor information-disclosure hardening against tuning an attack to just under the limit).

### 12.6 Security Headers
Full baseline in Section 13.8 — applied to every API response, not just HTML-serving routes (a JSON API is still a valid target for some header-dependent attacks, e.g., `X-Content-Type-Options: nosniff` prevents a browser from MIME-sniffing a JSON error response into something executable if it were ever loaded in a non-XHR context).

### 12.7 Error Responses
Single consistent `ErrorResponse` contract ([02-Architecture.md §12](02-Architecture.md#12-exception-flow--strategy), [04-API-Design.md §1](04-API-Design.md#1-conventions-applied-to-every-endpoint)) — detailed in Section 18 of [09-Security-Operations.md](09-Security-Operations.md#18-error-handling).

### 12.8 Request Validation
Section 9 — applied uniformly to every endpoint via the shared Bean Validation + Service-layer pipeline, never endpoint-specific ad hoc parsing.

### 12.9 Response Validation
Outbound DTOs are the *only* thing a controller can return (ADR-0009) — there is structurally no path for a Service to accidentally return a raw entity or an unvetted map that Jackson then serializes verbatim. As an additional CI-time control, the generated OpenAPI schema ([02-Architecture.md §2](02-Architecture.md#2-technology-stack-selection) — springdoc) is diffed against the previous version on every build; an unreviewed, unversioned field addition/removal on a published response DTO fails the build (ties to ADR-0012's versioning discipline and to threat #12, sensitive-data-exposure-by-accretion).

---

## 13. Application Security

### 13.1 CSRF Protection
Covered in Section 12.3 / [03-Security.md §9](03-Security.md#9-csrf); SDR-007 records the full decision including the alternative (synchronizer token pattern with server-side storage) considered and why the stateless double-submit variant was chosen instead, consistent with ADR-0003's no-server-side-session constraint.

### 13.2 XSS Prevention
Section 10 (Output Encoding) is the primary control; Section 13.8's CSP is the defense-in-depth backstop that limits the *impact* of any XSS that nonetheless occurs (e.g., via a future third-party script/dependency compromise) by restricting what a successfully injected script could do (no inline script execution, no arbitrary external script loading).

### 13.3 SQL Injection Prevention
100% parameterized data access via Spring Data JPA/Hibernate (derived queries, `Specification` predicates, and any native `@Query` always parameter-bound, never string-concatenated) — [03-Security.md §11](03-Security.md#11-input-validation--output-encoding); enforced additionally by static-analysis linting (a rule flagging any `String.format`/concatenation feeding into a query-construction API) as a CI gate, not review discipline alone.

### 13.4 Command Injection Prevention
No user-controlled input is ever passed to a shell/process-execution API (`Runtime.exec`, `ProcessBuilder`) with shell interpretation; the one plausible integration point (virus scanning, Section 11.4) is designed around a daemon-socket protocol specifically to avoid this class of vector entirely (threat #9) — if a future integration ever genuinely requires invoking an external process, the design mandates passing arguments as a pre-split array (never a shell-interpreted single string) and validating/allow-listing the executable path itself.

### 13.5 Open Redirect Prevention
The application does not implement a generic "redirect to `?returnUrl=`" pattern anywhere in this SRS's scope (no such feature exists in [01-SRS.md](01-SRS.md)) — this is itself the primary mitigation (nothing to attack). If a future OAuth2/SSO flow (Section 21 of [10-Security-Assurance.md](10-Security-Assurance.md#21-future-security-roadmap)) introduces a post-login redirect, the mandated design at that time is a strict allow-list of permitted redirect targets (relative paths within the SPA only, never an arbitrary absolute URL parameter) — recorded here as a standing constraint on that future work, not deferred without guidance.

### 13.6 Path Traversal Prevention
Section 11.6 (file storage keys are never derived from user input) is the primary instance; the same principle applies system-wide — no code path constructs a filesystem or resource path by concatenating user-supplied input, full stop.

### 13.7 Clickjacking Protection
`X-Frame-Options: DENY` and CSP `frame-ancestors 'none'` (Section 13.8) sent on every response — this is an internal helpdesk tool with no legitimate embedding use case (no partner site legitimately iframes this application), so the policy is maximally strict with no allow-listed exception.

### 13.8 HTTP Header Strategy

| Header | Value | Purpose |
|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Forces HTTPS for all future visits, closing the initial-plaintext-request window (threat #5); `preload` submission is a future-roadmap candidate once the production domain is finalized. |
| `X-Content-Type-Options` | `nosniff` | Prevents MIME-type sniffing that could turn a data response into executable content. |
| `X-Frame-Options` | `DENY` | Clickjacking (13.7); kept alongside CSP `frame-ancestors` for defense in depth against older browsers that don't honor CSP. |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'` | No inline scripts, no third-party script origins, no framing — see SDR-008 for the full derivation and the rollout plan (report-only mode first, Section 19 of [10-Security-Assurance.md](10-Security-Assurance.md#19-security-testing-strategy)). |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Avoids leaking full internal URLs (which may contain ticket IDs) to third-party destinations via the `Referer` header on any outbound link/resource load. |
| `Permissions-Policy` | `geolocation=(), camera=(), microphone=(), payment=()` | Explicitly disables browser features this application never legitimately needs, reducing the impact surface of any future compromised dependency. |
| `Cache-Control` | `no-store` on authenticated/sensitive responses | Prevents caching of ticket/user data in shared or persistent browser/proxy caches (threat #12). |
| `X-Permitted-Cross-Domain-Policies` | `none` | Legacy-plugin-era hardening; negligible cost to include. |

### 13.9 Secure Cookies
Full specification in [07-Security-Architecture.md §5.8](07-Security-Architecture.md#58-cookie-security).

### 13.10 HTTPS Enforcement
Mandatory in every non-`dev` profile: the HTTPS-enforcement filter (8.1, step 2) rejects/redirects plain-HTTP traffic, `Secure` cookie flags make cookies unusable over plain HTTP regardless, and HSTS (13.8) instructs the browser to never attempt plain HTTP again after the first successful HTTPS visit. TLS termination itself is an infrastructure-layer concern (load balancer/reverse proxy, Section 21 of [10-Security-Assurance.md](10-Security-Assurance.md#21-future-security-roadmap)) — the application-layer controls here are what ensure the application never *depends* on that termination point being correctly configured as its only line of defense.

### 13.11 Content Security Policy
See 13.8; full policy derivation, rollout strategy (report-only → enforced), and violation-reporting endpoint design recorded in SDR-008.
