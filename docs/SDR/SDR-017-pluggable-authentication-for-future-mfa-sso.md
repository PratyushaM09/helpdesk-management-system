# SDR-017: Pluggable Authentication Strategy for Future MFA/OAuth2/SSO/LDAP

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [10-Security-Assurance.md §21](../10-Security-Assurance.md#21-future-security-roadmap), [02-Architecture.md §4.1](../02-Architecture.md#41-authentication)

## Decision
Design `AuthenticationService` as an interface from the outset (already established architecturally in [02-Architecture.md §4.1](../02-Architecture.md#41-authentication)), with the local-password implementation as one concrete strategy among future others (OAuth2/OIDC federation, LDAP/Active Directory, SSO/SAML), all converging on the same internal JWT-issuance contract (Section 3 of [07-Security-Architecture.md](../07-Security-Architecture.md#3-authentication-architecture)). Reserve an explicit insertion point in the local login flow (between password verification and token issuance, Section 3.2 of [07-Security-Architecture.md](../07-Security-Architecture.md#32-login-flow-fr-auth-3)) for a future MFA/WebAuthn challenge step.

## Reason
This SDR exists to make an architectural commitment already implied by [02-Architecture.md](../02-Architecture.md) and SRS §15 explicit as a *security* decision, not just a structural one: every future authentication method named in the roadmap (Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)) must plug into the *same* downstream RBAC/session/audit pipeline (Sections 4–5 of [07-Security-Architecture.md](../07-Security-Architecture.md#4-authorization-architecture)) rather than each inventing its own. This matters specifically because a system with multiple *inconsistently secured* authentication paths is a well-known real-world source of security gaps — an attacker targets whichever path is weakest, and a bolted-on SSO integration that skips the account-lockout/audit-logging discipline the primary path enforces would be exactly that kind of gap.

## Alternatives Considered
- **Design only the current local-password flow now, redesign authentication when a second method is actually needed:** rejected — this is precisely the pattern that produces inconsistent, bolted-on future auth methods; committing to the interface boundary now, while there is only one implementation, costs almost nothing (the interface has exactly the shape the current implementation needs) and prevents a much larger later refactor.
- **Separate, parallel session/token issuance per authentication method (e.g., an OAuth2 login issues a differently-shaped token than local login):** rejected — would fragment the RBAC/audit/session-management design (Sections 4–5, 15–16) into per-method variants, multiplying the surface that must be independently secured and tested; every `AuthenticationService` implementation is required to converge on the one JWT contract precisely to avoid this.

## Pros
- Every future authentication method inherits the full existing security posture (RBAC, rate limiting, audit logging, session management) automatically, by construction, rather than needing to reimplement or remember to apply each control.
- The MFA insertion point is reserved and named now, meaning MFA adoption later is an additive step at a known location, not a flow redesign.
- Role mapping for federated methods (OAuth2/SSO/LDAP asserting a claim/group that must map to this system's three fixed roles) is isolated to each new implementation's own logic — the `Role`-as-table design ([05-Database.md §3](../05-Database.md#3-relationship-justification-cardinality-explained)) already supports this without a schema change.

## Cons
- A small amount of current design effort (defining the interface boundary and the JWT-issuance convergence point) that has no payoff until a second authentication method is actually built — accepted as a deliberate, low-cost investment consistent with this project's Future-Proof security goal, not speculative over-engineering, since the specific future methods are already named in SRS §15 rather than merely hypothetical.

## Future Impact
This is the SDR every future authentication-related roadmap item (Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap): OAuth2, Google/Microsoft login, MFA, TOTP, WebAuthn/Passkeys, SSO, LDAP/Active Directory) implements against — each is expected to be reviewed for conformance to this interface/convergence contract before being merged, rather than evaluated as an independent, one-off integration.
