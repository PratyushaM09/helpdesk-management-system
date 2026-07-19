# 09 — Enterprise Security Architecture Document (SecAD) — Part III

## HelpDesk Management System — Security Operations

| | |
|---|---|
| **Document Version** | 1.0 |
| **Status** | Accepted — Security Architecture Phase |
| **Part of** | [07-Security-Architecture.md](07-Security-Architecture.md) (Part I) · [08-Security-Controls.md](08-Security-Controls.md) (Part II) · Part III (this document) · [10-Security-Assurance.md](10-Security-Assurance.md) (Part IV) |

---

## Table of Contents (this part)

14. Database Security
15. Audit Strategy
16. Logging Strategy
17. Configuration Security
18. Error Handling

---

## 14. Database Security

### 14.1 Least Privilege (Database Roles)

The application does **not** connect to PostgreSQL as a superuser or schema-owner role in any non-`dev` environment. Three distinct database roles are defined:

| Role | Used by | Privileges |
|---|---|---|
| `helpdesk_app` | The running Spring Boot application (`prod`/`test`) | `SELECT`/`INSERT`/`UPDATE`/`DELETE` on application tables only. **No** `DROP`, `TRUNCATE`, `ALTER`, `CREATE`, or DDL privilege of any kind — the running application can never alter its own schema, even under a full application-layer compromise (a critical containment boundary: an attacker who achieves SQL injection or RCE within the app still cannot drop tables, alter constraints, or create a backdoor role). |
| `helpdesk_migrator` | The Flyway migration step of CI/CD only, never the running application | DDL privileges (`CREATE`/`ALTER`/`DROP` on application schema objects) — used exclusively during a controlled, reviewed deployment step, never held by a long-running, network-facing process. |
| `helpdesk_readonly` | Future reporting/BI/read-replica consumers (Section 21 of [10-Security-Assurance.md](10-Security-Assurance.md#21-future-security-roadmap)) | `SELECT` only — named here as a designed extension point, not yet provisioned, so that a future reporting tool never needs write credentials to answer a read-only question. |

This three-role separation is the concrete database-layer instance of the Least Privilege security goal ([07-Security-Architecture.md §1](07-Security-Architecture.md#1-security-goals)) and directly bounds the impact of threat #8 (SQL injection) even in the (already very unlikely, Section 2 of [07-Security-Architecture.md](07-Security-Architecture.md#2-threat-model)) case one is found: the application's own database credential is structurally incapable of a schema-destroying action.

### 14.2 Connection Security
- All application-to-database connections use TLS (`sslmode=require` at minimum, `verify-full` in `prod` once a managed database with a trusted CA-issued certificate is in place, e.g., the AWS RDS roadmap item) — the database credential and every row of query data traveling in transit is never sent in plaintext, even on infrastructure assumed to be "internal" (Zero Trust goal, [07-Security-Architecture.md §1](07-Security-Architecture.md#1-security-goals)).
- Connection pool (HikariCP, [02-Architecture.md §18](02-Architecture.md#18-performance-considerations)) is sized and timeout-bounded so a connection-exhaustion condition (accidental or attacker-induced, threat #21) fails fast with a clear error rather than hanging the application indefinitely.
- Network-level access to the database port is restricted to the application tier only (security-group/firewall-level allow-list) — an infrastructure control noted here as a requirement this design assumes will be satisfied by the deployment environment (SRS §12 places infrastructure-level network hardening out of this phase's direct scope, but the application's own credential/role design in 14.1 does not *rely* on that perimeter being perfect, consistent with Zero Trust).

### 14.3 Credential Storage
Database credentials (for all three roles in 14.1) are never committed to source control and never hardcoded in any `application-*.yml` (Section 17.1) — supplied exclusively via environment variables sourced from the deployment platform's secret store. Local `dev` credentials are a throwaway, non-sensitive local-only value (a local Postgres instance with no real data) and are still not committed as a literal — `.env`-style local override, itself gitignored.

### 14.4 Audit Logging (Database Level)
Database-level audit logging (e.g., PostgreSQL's `pgaudit` extension, logging DDL and privileged-role activity) is a recommended production hardening layer **beneath** the application-level audit trail (Section 15) — it exists to detect the scenario the application-level audit trail cannot see by definition: activity performed *outside* the application (e.g., a `helpdesk_migrator`-credentialed session, or any direct database access during an incident). Not required for local `dev`/`test`, mandatory for `prod` (recorded as a deployment-readiness checklist item, [02-Architecture.md §21](02-Architecture.md#21-future-architecture-roadmap)).

### 14.5 Encryption Requirements
- **In transit:** TLS on every application-to-database connection (14.2).
- **At rest:** disk/volume-level encryption is expected from the hosting platform (e.g., RDS encryption-at-rest, a configuration flag once the AWS roadmap item, [02-Architecture.md §21](02-Architecture.md#21-future-architecture-roadmap), is reached) — an infrastructure-layer control this design depends on being enabled, not one the application implements itself.
- **Column-level:** no column in the current data model ([05-Database.md §1](05-Database.md#1-entity-inventory)) requires application-level field encryption beyond password hashing (Section 6 of [07-Security-Architecture.md](07-Security-Architecture.md#6-password-policy) — hashing, not encryption, and deliberately so, since the value is never legitimately needed in reversible form). If a future feature introduces a genuinely sensitive reversible-need field (none currently exists in [01-SRS.md](01-SRS.md)'s scope), the mandated pattern at that time is envelope encryption via a managed KMS, recorded as a standing design constraint on such a feature, not deferred without guidance.

---

## 15. Audit Strategy

Two distinct, deliberately separate audit streams — restated from [02-Architecture.md §4.10](02-Architecture.md#410-audit) and ADR-0006 with the full tracked-event inventory this SecAD phase requires:

### 15.1 Ticket Activity Log (`TicketActivity`, ADR-0006)
Append-only, visible to ticket participants (creator, assignee, Administrators). Tracks: ticket creation, every status change, every priority/category change, every assignment/reassignment, every comment added, close, reopen, escalate — each with actor, timestamp, old/new value (FR-TICK-12).

### 15.2 Administrative / Security Audit Log (`AdminAuditLog`)
Append-only, **Administrator/compliance-only visibility** (never shown to a `USER`/`SUPPORT_ENGINEER`, even for actions taken against their own account — a deliberate asymmetry: a user is notified *that* their role changed via the notification system, ADR-0007, but the audit log's full detail, including actor and rationale, is a governance artifact, not a user-facing feature). Tracked event inventory:

| Event category | Specific events tracked |
|---|---|
| **Authentication** | Login success, login failure (with reason: bad password / account locked / unverified), logout, account lockout triggered, account unlocked (manual, by Admin). |
| **Credential changes** | Password change, password reset requested, password reset completed, forced global logout (`tokenVersion` bump) and its trigger reason. |
| **Role / permission changes** | Role assigned/changed (old role → new role, which Administrator performed it), account activated/deactivated. |
| **Ticket lifecycle (administrative angle)** | Ticket soft-deleted (by whom, stated reason — FR-TICK-5) — the ticket's own creation/update/status-change history lives in 15.1, but an Administrator's *deletion* action is additionally recorded here because it is specifically an administrative-authority action, not a normal workflow step. |
| **Category / configuration changes** | Category created, renamed, deactivated; any future system-configuration change (Section 4.9 of [02-Architecture.md](02-Architecture.md#49-administration)). |
| **Security violations** | Authorization failures (`403` responses — who attempted what they weren't allowed to, Section 16.2), CSRF validation failures, rate-limit trips, file-upload rejections (type/size/virus-scan failure), repeated failed-login patterns crossing the lockout threshold. |
| **File uploads** | Every attachment upload (ticket id/comment id, uploader, filename metadata, size, scan result) and every deletion. |
| **Data export** | Ticket list export, report export (who, what filter/date-range, when) — export is a data-exfiltration-shaped action even when fully authorized, and is deliberately audited as such. |

Every entry captures: actor (or "system" for an automated/scheduled-job-triggered event), action type, target type/id, a structured detail payload (`JSONB`, [05-Database.md §2](05-Database.md#2-entity-relationship-diagram)), timestamp, and — where the acting request is available — source IP and `traceId` (correlating an audit entry back to the full request log trail, Section 16).

### 15.3 Audit Retention Strategy
- **Ticket activity log:** retained for the full life of its parent ticket, including through soft-deletion (ADR-0005) — never purged independently of a future, deliberately-scoped data-retention/archival job ([02-Architecture.md §21](02-Architecture.md#21-future-architecture-roadmap) item 10, SRS §17.10).
- **Administrative/security audit log:** retained a minimum of **1 year** in immediately queryable storage (a common baseline for internal-tool compliance/incident-investigation needs), configurable per organizational policy once one is formally defined (not fixed by the current SRS, which explicitly leaves data retention as a deliberate future decision, SRS §17.10) — this SecAD sets the floor default rather than leaving retention undefined-by-omission.
- **No audit entry is ever hard-deleted by the application** — both logs are append-only by construction (ADR-0006 extended to `AdminAuditLog`); any eventual archival (e.g., moving records older than the retention window to cold storage) is a separate, explicitly-scoped operation, never an implicit side effect of any other feature.
- **Tamper-resistance:** because both logs expose no `UPDATE`/`DELETE` path at the repository layer (by omission, per ADR-0006), the primary tamper vector is direct database access outside the application — which is exactly what the database-level audit logging in Section 14.4 is designed to independently detect.

---

## 16. Logging Strategy

Extends [02-Architecture.md §13](02-Architecture.md#13-logging-flow--strategy) with the security-specific logging inventory and hard exclusions this SecAD phase requires.

### 16.1 What Is Logged (Security-Relevant)

| Category | Logged detail | Level |
|---|---|---|
| **Authentication events** | Login success/failure (with generic reason category, never "which field was wrong"), logout, token refresh, token-refresh-reuse-detection trip, lockout triggered/cleared. | INFO (success), WARN (failure/lockout) |
| **Authorization failures** | Every `403` — principal (user id), attempted resource/action, timestamp. | WARN |
| **Brute-force / credential-stuffing indicators** | Rate-limit trips on `/auth/**` (Section 12.5 of [08-Security-Controls.md](08-Security-Controls.md#125-rate-limiting)), lockout-threshold crossings, unusually high per-IP failed-login volume. | WARN |
| **CSRF failures** | Every rejected state-changing request due to CSRF mismatch — principal (if any), endpoint, timestamp. | WARN |
| **File upload violations** | Disallowed type, oversized file, failed virus scan — uploader, ticket/comment context, rejection reason category (not the raw malware signature in the client-facing message, Section 11.4 of [08-Security-Controls.md](08-Security-Controls.md#114-virus-scanning-strategy); full detail is acceptable server-side-log-only). | WARN |
| **Security exceptions** | Any `AuthenticationException`/`AccessDeniedException`/`ConflictException` (optimistic-lock) surfaced by the Global Exception Handler ([02-Architecture.md §12](02-Architecture.md#12-exception-flow--strategy)). | WARN (client-driven) / ERROR (unexpected internal cause) |
| **Unhandled exceptions** | Full stack trace, server-side only. | ERROR |

Every log line carries the request's `traceId` (Section 13 of [02-Architecture.md](02-Architecture.md#13-logging-flow--strategy)), so a security event can be correlated end-to-end with the rest of that request's application-log trail without needing to also expose that detail to the client.

### 16.2 What Is Never Logged, At Any Level
- **Passwords** — plaintext, at any point, in any log statement, including DEBUG (there is no legitimate debugging need that justifies this; it is a standing, non-negotiable rule).
- **Password hashes** — even though one-way, a logged hash is unnecessary exposure with no operational value.
- **Raw JWTs, refresh tokens, or CSRF tokens** — a logged token is functionally equivalent to a leaked credential for its validity window; logs use the associated user id / a truncated non-reversible token identifier (e.g., a hash prefix) for correlation instead, never the token itself.
- **Secrets** — database credentials, mail credentials, signing keys (Section 17) — never logged, never included in an exception message that might be logged (a `DataSource` connection failure, for example, is logged with the target host/port only, never the credential used).
- **Full sensitive user information at INFO/DEBUG** — a request/response body containing PII is not logged in full outside a deliberately scoped, short-lived DEBUG session that is never enabled in `prod` (Section 17); routine INFO-level business logs reference identifiers (user id, ticket id), not full personal data payloads.
- **Enforcement mechanism:** entity/DTO classes carrying any of the above exclude the field from `toString()`/logging-framework structured output by construction (no field simply "happens" to be loggable — this mirrors ADR-0009's allow-list philosophy applied to logging, not just API responses), and a log-scrubbing/lint check is a recommended CI addition (Section 19 of [10-Security-Assurance.md](10-Security-Assurance.md#19-security-testing-strategy)) that fails a build introducing an obvious credential-shaped field into a log statement.

### 16.3 Log Transport & Format
Structured (JSON) logging in `test`/`prod`, human-readable in `dev` ([02-Architecture.md §13](02-Architecture.md#13-logging-flow--strategy)) — structured logs are what makes a future SIEM/log-aggregation integration (Splunk, ELK, CloudWatch Logs) a shipping-configuration change, not a logging-rewrite, and is what makes the security-event categories in 16.1 mechanically queryable/alertable once such a pipeline exists (Section 21 of [10-Security-Assurance.md](10-Security-Assurance.md#21-future-security-roadmap)).

---

## 17. Configuration Security

Extends [02-Architecture.md §17](02-Architecture.md#17-configuration-strategy) with the security-specific requirements this SecAD phase governs.

### 17.1 Environment Variables & Secret Management
No secret (database credentials for any of the three roles in 14.1, JWT signing key, mail-provider credentials, future OAuth2 client secrets) is ever committed to source control, ever hardcoded in any `application-*.yml`, or ever present in a Docker image layer — every secret is injected at runtime via environment variable, sourced from:
- **`dev`:** a local, gitignored `.env` file (or IDE-level environment configuration) — non-sensitive throwaway values only.
- **`test`/CI:** the CI platform's own secret store (e.g., GitHub Actions Encrypted Secrets), scoped to the pipeline that needs them.
- **`prod`:** the deployment platform's secret manager — this design explicitly targets a future migration to a dedicated secret manager (AWS Secrets Manager / HashiCorp Vault, Section 21 of [10-Security-Assurance.md](10-Security-Assurance.md#21-future-security-roadmap)) as the eventual `prod` source, with plain environment-variable injection as the interim mechanism; because the application only ever reads `${ENV_VAR}` placeholders (never a literal), this migration requires zero application code change when it happens — a supply-side change only.
- **Pre-commit secret scanning** (e.g., gitleaks/truffleHog integrated into the CI pipeline, [02-Architecture.md §16](02-Architecture.md#16-dependency--configuration-hardening)) is a mandatory CI gate, not an optional tooling suggestion, given how common accidental-secret-commit is as a real-world breach cause.

### 17.2 JWT Signing Key Management
The JWT signing key (RS256 private key in `prod`, per ADR-0003/Section 3 of [07-Security-Architecture.md](07-Security-Architecture.md#3-authentication-architecture)) is treated with the highest sensitivity of any secret in the system — its compromise allows forging valid authentication for any user/role. It is provisioned via the same secret-manager path as 17.1, rotated on a defined schedule (recommended: annually, or immediately on suspected compromise) via a documented key-rotation procedure that supports a brief dual-key verification window (old key still accepted for verification, new key used for signing) so in-flight tokens aren't invalidated mid-rotation — recorded as a standing operational requirement for whoever operates `prod`, not an afterthought.

### 17.3 Database Credentials
Section 14.3 — environment-variable-injected, role-scoped per 14.1, never a literal in any committed file.

### 17.4 Mail Credentials
Configured (`MailProperties`, [02-Architecture.md §17](02-Architecture.md#17-configuration-strategy)) but inert in this phase (Assumption A7) — when email delivery is enabled (ADR-0007's second listener, Section 21 of [10-Security-Assurance.md](10-Security-Assurance.md#21-future-security-roadmap)), SMTP/API credentials for the mail provider follow the identical environment-variable/secret-manager pattern as database credentials — no exception carved out for "less sensitive" categories of secret.

### 17.5 File Upload Paths
The local-disk storage root (`LocalDiskStorageService`, ADR-0008) is configured via `FileUploadProperties`, is never a path under the web server's publicly-servable document root (Section 11.5 of [08-Security-Controls.md](08-Security-Controls.md#115-storage-strategy)), and is never derived from or influenced by any request-scoped/user-supplied value (Section 11.6 of [08-Security-Controls.md](08-Security-Controls.md#116-random-file-names--path-validation)) — it is a fixed, deployment-time configuration value.

### 17.6 Profiles
`dev` / `test` / `prod` (plus any CI-specific profile) — [02-Architecture.md §17](02-Architecture.md#17-configuration-strategy). Security-relevant profile differences are treated as a reviewed matrix, not ad hoc per-environment tweaks:

| Setting | `dev` | `test` | `prod` |
|---|---|---|---|
| HTTPS enforcement | Relaxed (`localhost` exception) | Enforced (against a TLS-terminating test harness or self-signed cert) | Enforced, HSTS active |
| CORS origin | Local dev server only | Test-harness origin only | Deployed SPA origin(s) only — never a wildcard |
| Log verbosity | DEBUG permitted | INFO, structured | INFO/WARN, structured, no DEBUG ever enabled |
| Virus scan (11.4) | No-op/pass-through | Real scanner against test fixtures (including EICAR test file) | Mandatory, fail-closed |
| Swagger UI / OpenAPI exposure | Enabled, unauthenticated | Enabled | Disabled, or authenticated-Admin-only — a public `prod` Swagger UI is an information-disclosure and attack-surface-mapping aid to an attacker and is explicitly not shipped open |
| Actuator endpoints | Full detail, local only | Full detail, CI-internal only | Health/readiness only on a public port; full detail (`/actuator/env`, `/actuator/beans`) on an internal management port only, itself authenticated |
| Rate limiting | Relaxed/disabled for local iteration | Enabled (validates the control itself) | Fully enforced (Section 12.5 of [08-Security-Controls.md](08-Security-Controls.md#125-rate-limiting)) |

### 17.7 Production Configuration Hardening Checklist
A standing pre-deployment gate (referenced again in Section 19 of [10-Security-Assurance.md](10-Security-Assurance.md#19-security-testing-strategy)):
- No secret present in any committed file (17.1, CI-enforced).
- `prod` profile active (never defaulting to `dev`'s relaxed settings if the profile flag is accidentally omitted — the application's default profile, if unset, is `prod`'s strictest posture, not `dev`'s most permissive one — a deliberate fail-safe default).
- TLS/HSTS active, Swagger UI closed or gated, Actuator detail endpoints internal-only, DEBUG logging off, rate limiting and virus scanning enforced.

---

## 18. Error Handling

Extends [02-Architecture.md §12](02-Architecture.md#12-exception-flow--strategy) with the security-specific rationale and the exact information-disclosure boundary this SecAD phase enforces.

### 18.1 What Is Never Exposed To The Client
- **Stack traces** — under no circumstance, in any profile including `dev` for any externally-reachable response (a `dev`-only verbose mode, if ever added for local debugging convenience, is explicitly scoped to responses only visible on `localhost`, never shipped toggled-on in a deployed environment).
- **SQL queries / ORM-generated query fragments** — a constraint-violation or query failure is translated to a generic `ConflictException`/`400`/`500` category by the Global Exception Handler; the underlying SQL is available only in the server-side ERROR log (Section 16), correlated via `traceId`.
- **File paths** — no response ever includes a server filesystem path (relevant especially to Section 11's file-handling code, where an internal storage path must never leak even in an error message about a failed upload/download).
- **Framework/library internals** — no response includes a fully-qualified exception class name, a framework version banner, or a dependency name/version (all of which aid an attacker's reconnaissance/fingerprinting).
- **Version numbers** — the application does not advertise its own version, the database engine version, or dependency versions in any response header or body (a custom `Server` header suppression / generic value is applied, overriding any framework default that would otherwise announce this).

### 18.2 Error Response Structure
Every error, from every layer, converges on the single contract already defined in [02-Architecture.md §12](02-Architecture.md#12-exception-flow--strategy) and [04-API-Design.md §1](04-API-Design.md#1-conventions-applied-to-every-endpoint):

```
{
  "errorCode": "<stable machine-readable code>",
  "message": "<plain-language, actionable, no internal detail>",
  "violations": [ { "field": "...", "reason": "..." } ],   // 400s only
  "timestamp": "<ISO-8601>",
  "traceId": "<correlates to server-side logs>"
}
```

- **`errorCode`** is a stable enum-like string (e.g., `TICKET_INVALID_TRANSITION`, `AUTH_INVALID_CREDENTIALS`) — safe to expose because it is a *category*, not a detail; it lets a client (or a future automated integration) branch on error type without parsing a human-readable string, and lets the SPA render a tailored message without the server needing to embed presentation logic in the message text itself.
- **`message`** is always plain-language and actionable (SRS §8's explicit requirement, Constraint C4) — written from a fixed, reviewed message catalog per `errorCode`, never string-built from internal exception detail (which is exactly how stack-trace/SQL-fragment leaks happen in practice — by a developer convenience shortcut of `message: ex.getMessage()`, which this design explicitly forbids as a pattern).
- **`traceId`** is the *sanctioned* channel for an engineer to go find the real detail — the client gets enough to report an issue precisely, without the response itself carrying anything sensitive.
- **Distinguishing existence from authorization deliberately, not accidentally:** as established in Section 2 (threat #26) and Section 4 of [07-Security-Architecture.md](07-Security-Architecture.md#4-authorization-architecture), a resource that exists but that the caller isn't authorized to see returns `404`, not `403` — a `403` would confirm the resource's existence to an unauthorized caller, which is itself a (minor) information disclosure; this rule is applied consistently, not case-by-case, across every ownership-scoped endpoint ([04-API-Design.md](04-API-Design.md)).
- **Generic `5xx` fallback:** any exception type not explicitly mapped by the Global Exception Handler falls through to a single generic `500` message ("Something went wrong — please try again, and contact support with reference `traceId` if this continues") — this is the deliberate catch-all that guarantees no *new*, unanticipated exception type introduced by a future code change can accidentally leak detail simply because nobody thought to write a specific handler for it yet (secure-by-default applied to exception handling itself).
