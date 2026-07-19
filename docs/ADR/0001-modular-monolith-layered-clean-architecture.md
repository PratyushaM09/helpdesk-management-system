# ADR-0001: Modular Monolith with Layered / Clean Architecture

**Status:** Accepted
**Date:** 2026-07-19

## Context

The SRS (Section 8, Maintainability & Extensibility) requires a codebase that stays modular and extensible without cross-cutting rewrites, and that does not preclude a future move toward microservices (SRS §15). At the same time, Constraint C2 scopes this as a single-team, portfolio-scale build. A microservices split today would impose network boundaries, distributed transactions, and operational overhead (service discovery, distributed tracing, inter-service auth) that this team and this scale do not need, while a naive "big ball of mud" monolith would violate the maintainability requirement and make a future split much harder.

## Decision

Build a **modular monolith**: a single deployable backend application internally partitioned into strict, independently reasoned-about modules (Authentication, Users, Tickets, Comments, Attachments, Notifications, Reports, Dashboard, Administration, Audit, Configuration — see [02-Architecture.md](../02-Architecture.md#module-breakdown)), each internally structured using **Clean Architecture / layered architecture**:

- **Controller (interface) layer** — HTTP concerns only.
- **Service layer** — business rules and orchestration, framework-agnostic where practical.
- **Repository layer** — persistence, talks only to the database.
- **Domain (entity) layer** — core business objects, has no dependency on outer layers.

Dependencies point inward only: Controller → Service → Repository → Entity. No layer reaches "sideways" into another module's repository or entity directly — cross-module interaction happens through a module's public service interface only.

## Consequences

- **Positive:** One deployable artifact, one database connection pool, no distributed-transaction problem, simple local development and testing, straightforward CI/CD.
- **Positive:** Because module boundaries are enforced by convention and package structure now, a future extraction of (for example) the Notification or Reports module into its own microservice (SRS §15) is a mechanical exercise — replace an in-process service call with a REST/queue call — not a redesign.
- **Negative:** Requires discipline; nothing at the language level stops a developer from importing across module boundaries. Mitigated by code review checklist and package-structure convention (ADR-0011) and, if adopted later, ArchUnit tests that fail the build on illegal cross-module repository access.
- **Alternatives considered:**
  - *Microservices from day one* — rejected: operational cost (service mesh, distributed tracing, per-service CI/CD, network-boundary auth) is unjustified at current scale (C2) and premature per SRS §12 (multi-tenancy and mobile clients are explicitly out of scope for this phase).
  - *Unstructured monolith (transaction-script, fat controllers)* — rejected: directly violates SRS §8 Maintainability and the "controllers must never contain business logic" mandate.
