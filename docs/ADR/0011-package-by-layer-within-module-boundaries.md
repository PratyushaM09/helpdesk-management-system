# ADR-0011: Package-by-Feature-Module, then by-Layer Within Each Module

**Status:** Accepted
**Date:** 2026-07-19

## Context

Two common package organization styles exist: strict package-by-layer at the application root (`com.helpdesk.controller.*`, `com.helpdesk.service.*`, all modules' classes mixed together per layer) versus package-by-feature (`com.helpdesk.ticket.*`, `com.helpdesk.user.*`, each containing its own mixed concerns). ADR-0001 commits to a modular monolith with enforced module boundaries; the package structure must make those boundaries visible and enforceable, while still keeping the layered dependency rule (Controller→Service→Repository) legible within each module.

## Decision

Use a **hybrid**: top-level packages by module (feature), and within each module, sub-packages by layer:

```
com.helpdesk.ticket.controller
com.helpdesk.ticket.service
com.helpdesk.ticket.service.impl
com.helpdesk.ticket.repository
com.helpdesk.ticket.entity
com.helpdesk.ticket.dto
com.helpdesk.ticket.mapper
com.helpdesk.ticket.validation
```

Cross-cutting, module-agnostic concerns (`security`, `config`, `exception`, `audit`, `notification` infrastructure, `scheduler`, `event`, `constants`, `util`, `interceptor`, `filter`) remain top-level, shared packages — see the full rationale per package in [02-Architecture.md](../02-Architecture.md#package-structure).

## Consequences

- **Positive:** A developer working on Tickets finds everything ticket-related under one root package — high cohesion, matching the brief's explicit objective. Module boundaries (ADR-0001) are visible in the package tree, not just convention in someone's head.
- **Positive:** If a module is later extracted into its own microservice/deployable (SRS §15), its package is already a near-complete bounded context — copy the package, add its own `Application` entry point and `pom.xml`, done.
- **Negative:** Slightly more nested packages than flat package-by-layer; considered a worthwhile trade for module cohesion.
- **Alternatives considered:**
  - *Pure package-by-layer* (`controller`, `service`, `repository` at root, all modules mixed inside) — rejected: obscures module boundaries, makes ADR-0001's "no module reaches into another's repository" rule much harder to see or enforce, and is the conventional style this brief explicitly says not to blindly accept.
  - *Pure package-by-feature with no layer sub-packaging* — rejected: within a large module (Tickets has ~12 FRs), a flat feature package becomes a dumping ground; layer sub-packages keep Controller/Service/Repository separation explicit even inside one module.
