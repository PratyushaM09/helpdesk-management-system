# SDR-016: No Forced Periodic Password Expiration

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [07-Security-Architecture.md §3.10](../07-Security-Architecture.md#310-password-expiration)

## Decision
Do not implement mandatory periodic password rotation (e.g., "must change every 90 days"). Password change remains fully user-initiated (Section 3.6 of [07-Security-Architecture.md](../07-Security-Architecture.md#36-password-change-flow-fr-auth-6)) or triggered by a specific event (suspected compromise, Administrator-forced reset).

## Reason
Mandatory periodic rotation was, for many years, treated as an uncontroversial best practice and appears in many legacy compliance checklists. Current authoritative guidance has reversed this position: NIST Special Publication 800-63B explicitly recommends against requiring periodic password changes absent evidence of compromise, and OWASP's Authentication Cheat Sheet concurs. The empirical reasoning is specific and well-documented: forced rotation measurably drives users toward predictable, weakly-varied password patterns (incrementing a digit or a season name), which *reduces* real-world password strength over time rather than improving it, while imposing recurring friction (and help-desk password-reset load) with no corresponding security gain against the actual dominant attack modes this system's threat model identifies (credential stuffing from external breaches, brute force, and phishing — none of which are meaningfully mitigated by a password having a maximum age).

## Alternatives Considered
- **Fixed rotation interval (e.g., 90 days) for all accounts:** rejected per the reasoning above — this is the conventional default this decision deliberately departs from, and departs from with a documented rationale rather than by omission, precisely because a reviewer might otherwise assume its absence is an oversight.
- **Fixed rotation interval for `ADMIN` accounts only, given their elevated blast radius:** considered as a narrower version. Rejected for the same evidence-based reasoning — the elevated risk of an Administrator account is better addressed by mandatory MFA for that role once introduced (Section 4.3 of [07-Security-Architecture.md](../07-Security-Architecture.md#43-role-admin), Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)) than by password aging, which doesn't address the actual threat (a strong, long-lived password is not weaker than a frequently-rotated weaker one).
- **Breach-database check on password set (e.g., against a Pwned-Passwords-style corpus), instead of/alongside no forced rotation:** not adopted in this phase but named as the recommended companion control and future-roadmap candidate (Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)) — this is the evidence-based alternative to periodic rotation for the actual risk rotation was originally meant to address (a password becoming known through a breach elsewhere), since it detects the specific compromise condition directly rather than blindly aging every password on a fixed timer regardless of whether it was ever exposed.

## Pros
- Avoids driving users toward weaker, predictable password-rotation patterns — a documented real-world failure mode of the rejected alternative.
- Reduces help-desk/support burden (password-reset requests spike measurably after forced-rotation deadlines in organizations that use them) without a corresponding security cost.
- Aligns with current NIST/OWASP guidance, which is itself a defensible position if this design is ever externally audited (an auditor following outdated "90-day rotation" checklists is the one out of step with current guidance, and this SDR's documented reasoning is the evidence to present in that conversation).

## Cons
- Departs from some legacy compliance frameworks/checklists that still list periodic rotation as a checkbox requirement — if this system is ever deployed into an organizational context bound by such a framework, this decision would need to be revisited against that specific framework's actual (not merely historically-assumed) requirements.
- A compromised-but-undetected password (one that leaked without the user's or the system's knowledge) could remain valid indefinitely absent event-driven detection — directly why the future breach-database-check roadmap item (above) is the recommended complementary control, not a reason to reinstate blind periodic rotation.

## Future Impact
MFA (Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)) is the primary planned mitigation for the residual risk a long-lived password represents — once introduced, a compromised password alone is no longer sufficient for account takeover, which is a stronger security property than periodic rotation would have provided even under the rejected alternative.
