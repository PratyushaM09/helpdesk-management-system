# SDR-004: Two-Layer RBAC Enforcement

**Status:** Accepted
**Date:** 2026-07-19
**Extends:** ADR-0004 (Defense-in-Depth RBAC)
**Related:** [07-Security-Architecture.md §4.4](../07-Security-Architecture.md#44-method-level-authorization-strategy)

## Decision
Enforce authorization at both the URL/filter level (coarse, role-only) and the Service-method level (fine, ownership-aware via `@PreAuthorize` + custom `PermissionEvaluator`), with the fine-grained check placed on Service **interfaces**, never relied upon at the Controller layer alone.

## Reason
This is the Security Architecture phase's formal ratification of ADR-0004, recorded as an SDR because it is the single most consequential control against the highest-severity threats in the model: Broken Access Control (OWASP A01), BOLA/IDOR, and BFLA (threats #22/#23). A single enforcement point creates a single point of failure — one missed annotation on one new endpoint is a full authorization bypass if nothing else backs it up.

## Alternatives Considered
- **URL-level only:** rejected — cannot express ownership rules (a `USER` must see only *their own* tickets, not merely "any ticket, because they hold the USER role"); this alone would leave BOLA/IDOR completely unmitigated.
- **Method-level only, no URL gating:** rejected — every unauthorized request would still reach the Service layer (and, transitively, load some data via `PermissionEvaluator`) before being rejected, which is both a weaker fail-fast posture and marginally worse for DoS resistance (threat #21) than rejecting at the filter chain.
- **Attribute-Based Access Control (ABAC) / a full policy-engine (e.g., OPA):** considered as a more general model. Rejected for this phase as disproportionate complexity — three fixed roles with a small number of ownership predicates (Section 4 of [07-Security-Architecture.md](../07-Security-Architecture.md#4-authorization-architecture)) do not yet justify a general-purpose policy language; the `PermissionEvaluator` approach is a lighter-weight instance of the same idea and is a viable stepping stone toward ABAC/OPA later if role/permission complexity genuinely grows (e.g., per-organization policies under a future multi-tenancy expansion, explicitly out of scope per SRS §12).

## Pros
- Redundant enforcement — a gap in one layer is still caught by the other, verified explicitly by security tests that disable one layer at a time (Section 19.2 of [10-Security-Assurance.md](../10-Security-Assurance.md#192-authorization-testing)).
- Fail-fast: the cheap check (URL/role) runs before the more expensive check (ownership, which may require a DB lookup).
- Method-level placement on the Service *interface* protects every future caller of that method (a scheduled job, a future GraphQL resolver), not just today's one Controller — directly supports the Zero Trust goal.

## Cons
- Two places to keep in sync when a new role/permission is introduced — mitigated by centralizing role/permission constants in one shared package (Section 4.4 of [07-Security-Architecture.md](../07-Security-Architecture.md#44-method-level-authorization-strategy)) as the single source both layers read from.
- Slightly more annotation/configuration surface than a single-layer design — accepted deliberately, consistent with this project's explicit "maintainability over shorter code" mandate.

## Future Impact
If role/permission complexity grows meaningfully (e.g., a future per-category or per-team permission dimension), the `PermissionEvaluator` layer is the natural place to absorb that complexity without touching the URL-level layer at all — the two layers can evolve at different rates because they were deliberately kept independent rather than merged into one mechanism.
