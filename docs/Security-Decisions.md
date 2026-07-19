# Security Decisions Index

A running, one-line-per-entry index of every accepted Security Decision Record (SDR). This file is an index only — the reasoning lives in [SDR/](SDR/); do not duplicate content here. SDRs are the security-specific counterpart to the architecture ADRs indexed in [Decisions.md](Decisions.md); some SDRs explicitly extend an existing ADR rather than duplicating it (noted in the "Extends" column).

| SDR | Title | Status | Date | Extends |
|---|---|---|---|---|
| [SDR-001](SDR/SDR-001-bcrypt-password-hashing.md) | BCrypt Password Hashing with Cost Factor 12 | Accepted | 2026-07-19 | — |
| [SDR-002](SDR/SDR-002-httponly-cookie-token-delivery.md) | HttpOnly/Secure/SameSite Cookie Delivery for JWT | Accepted | 2026-07-19 | ADR-0003 |
| [SDR-003](SDR/SDR-003-refresh-token-rotation-reuse-detection.md) | Refresh Token Rotation with Reuse Detection | Accepted | 2026-07-19 | — |
| [SDR-004](SDR/SDR-004-two-layer-rbac-enforcement.md) | Two-Layer RBAC Enforcement | Accepted | 2026-07-19 | ADR-0004 |
| [SDR-005](SDR/SDR-005-account-lockout-policy.md) | Account Lockout Policy (5 Attempts / 15-Minute Window) | Accepted | 2026-07-19 | — |
| [SDR-006](SDR/SDR-006-remember-me-via-refresh-token.md) | "Remember Me" via Sliding Refresh Token, Not a Separate Mechanism | Accepted | 2026-07-19 | — |
| [SDR-007](SDR/SDR-007-double-submit-csrf-protection.md) | Double-Submit CSRF Protection over Server-Side Synchronizer Tokens | Accepted | 2026-07-19 | — |
| [SDR-008](SDR/SDR-008-strict-content-security-policy.md) | Strict Content Security Policy with Report-Only Rollout | Accepted | 2026-07-19 | — |
| [SDR-009](SDR/SDR-009-generic-error-responses.md) | Generic, Catalog-Based Error Messages (No Exception-Detail Passthrough) | Accepted | 2026-07-19 | — |
| [SDR-010](SDR/SDR-010-file-upload-defense-in-depth.md) | File Upload Defense in Depth (Magic-Byte Validation + Virus Scanning) | Accepted | 2026-07-19 | ADR-0008 |
| [SDR-011](SDR/SDR-011-database-role-separation.md) | Database Role Separation (App / Migrator / Read-Only) | Accepted | 2026-07-19 | — |
| [SDR-012](SDR/SDR-012-dual-audit-log-streams.md) | Dual Audit Log Streams — Ticket Activity vs. Administrative/Security | Accepted | 2026-07-19 | ADR-0006 |
| [SDR-013](SDR/SDR-013-layered-rate-limiting.md) | Layered Rate Limiting (Global IP, Auth-Specific, Per-User) | Accepted | 2026-07-19 | — |
| [SDR-014](SDR/SDR-014-security-header-baseline.md) | Security Header Baseline Applied to Every Response | Accepted | 2026-07-19 | — |
| [SDR-015](SDR/SDR-015-secret-management-progressive-hardening.md) | Progressive Secret Management (Env Vars Now, Managed Secret Store Later) | Accepted | 2026-07-19 | — |
| [SDR-016](SDR/SDR-016-no-forced-password-expiration.md) | No Forced Periodic Password Expiration | Accepted | 2026-07-19 | — |
| [SDR-017](SDR/SDR-017-pluggable-authentication-for-future-mfa-sso.md) | Pluggable Authentication Strategy for Future MFA/OAuth2/SSO/LDAP | Accepted | 2026-07-19 | — |

Update this table whenever an SDR in [SDR/](SDR/) is added or changes status.
