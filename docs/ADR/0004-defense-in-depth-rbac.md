# ADR-0004: Defense-in-Depth RBAC (URL-level + Method-level)

**Status:** Accepted
**Date:** 2026-07-19

## Context

SRS Acceptance Criterion 1 requires role separation to be *enforced, not just hidden in the UI*. Three roles exist (User, Support Engineer, Administrator) with overlapping-but-distinct permissions on the same resources (e.g., a ticket is readable by its creator, its assigned engineer, and any Administrator, but writable differently by each). A single enforcement point is not sufficient because the "who can see this specific ticket" rule is data-dependent (ownership), not just role-dependent.

## Decision

Enforce authorization at **two layers**, deliberately redundant:

1. **URL/filter-level (coarse-grained):** Spring Security's filter chain restricts entire route prefixes by role (e.g., `/api/v1/admin/**` requires `ADMIN`). This is the first gate and rejects obviously unauthorized calls before they reach any business logic.
2. **Method-level (fine-grained, data-aware):** `@PreAuthorize` annotations on service-layer methods, backed by a custom `PermissionEvaluator` / Specification-pattern checks (ADR-0004 pairs with the Specification pattern in [02-Architecture.md](../02-Architecture.md#design-patterns)) for ownership rules that can't be expressed as a static role check — e.g., "a User may view this ticket only if `ticket.createdBy == currentUser.id`."

Business rule ownership checks live in the **Service layer**, never the Controller — consistent with ADR-0001's "controllers must never contain business logic."

## Consequences

- **Positive:** A bug or omission in one layer (e.g., a new controller route added without a class-level role annotation) is still caught by the method-level check, and vice versa — satisfies the acceptance criterion that role separation is genuinely enforced.
- **Positive:** The RBAC matrix ([03-Security.md](../03-Security.md#rbac-matrix)) is testable in isolation at the service layer without spinning up HTTP, keeping the testing pyramid ([06-Testing.md](../06-Testing.md)) cheap.
- **Negative:** Two places to keep in sync when a new role or permission is introduced; mitigated by centralizing role/permission constants in the `constants` package (single source of truth referenced by both layers) and covering the matrix with a dedicated security-test suite (SRS §16, Acceptance Criterion 1).
- **Alternatives considered:**
  - *URL-level only* — rejected: cannot express ownership-based rules (a User must only ever see *their own* tickets, not all tickets of their role).
  - *Method-level only* — rejected: leaves a window where an unauthenticated or wrong-role request reaches deeper into the stack before rejection, worse for both security posture and performance (fail fast).
