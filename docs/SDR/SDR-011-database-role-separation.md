# SDR-011: Database Role Separation (App / Migrator / Read-Only)

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [09-Security-Operations.md §14.1](../09-Security-Operations.md#141-least-privilege-database-roles)

## Decision
Define three distinct PostgreSQL roles — `helpdesk_app` (DML only: `SELECT`/`INSERT`/`UPDATE`/`DELETE`, no DDL), `helpdesk_migrator` (DDL, used only by the CI/CD migration step, never by the running application), and `helpdesk_readonly` (SELECT-only, reserved for future reporting/BI consumers). The running application always connects as `helpdesk_app`.

## Reason
Least Privilege is a stated top-level security goal (Section 1 of [07-Security-Architecture.md](../07-Security-Architecture.md#1-security-goals)). The application's own database credential is the single most consequential secret from a blast-radius perspective — if the application layer is ever compromised (via any vulnerability, known or unknown), whatever privileges its database credential holds are what the attacker inherits. Structurally denying that credential any DDL capability means even a full application-layer compromise (RCE, an unforeseen SQL injection despite Section 13.3 of [08-Security-Controls.md](../08-Security-Controls.md#133-sql-injection-prevention)) cannot escalate to schema destruction, backdoor-role creation, or `TRUNCATE`-style data destruction beyond what the application's own row-level operations already permit.

## Alternatives Considered
- **Single database role for both migrations and runtime application traffic:** the common default in many tutorials/starter setups. Rejected — it means the everyday, network-facing, highest-exposure credential also holds the most dangerous privilege tier (schema alteration), which is precisely backwards from a least-privilege posture.
- **Superuser/schema-owner role for the application (simplest to set up):** rejected outright — the maximal violation of least privilege; explicitly the anti-pattern this decision exists to avoid.
- **Per-module database roles (a distinct role per bounded context, e.g., `ticket_app`, `user_app`):** considered as a finer-grained alternative, mirroring the modular-monolith module boundaries (ADR-0001). Rejected for this phase as disproportionate operational complexity for a single-schema, single-database deployment (SRS §12) — the three-tier split (app/migrator/readonly) already captures the highest-value privilege boundary (runtime vs. schema-changing vs. read-only); per-module DB roles are a plausible refinement only if/when the modular monolith is actually decomposed toward microservices ([02-Architecture.md §21](../02-Architecture.md#21-future-architecture-roadmap) item 12), at which point each extracted service naturally gets its own credential anyway.

## Pros
- Bounds the impact of a full application-layer compromise to data-level (DML) damage, never schema-level.
- Migrations run under a distinct, narrowly-scoped, non-long-running credential — reducing the window and surface where the most powerful database privilege is exposed.
- Establishes the `helpdesk_readonly` role as a pre-defined extension point, so a future reporting/BI tool never needs to be granted write access just to answer a read-only question.

## Cons
- Slightly more setup/operational complexity (three roles and their grants to provision and keep in sync with schema changes) than a single-role setup — a one-time cost, absorbed into the deployment/migration tooling, not a recurring developer burden.

## Future Impact
If the system moves toward microservices (SRS §15, [02-Architecture.md §21](../02-Architecture.md#21-future-architecture-roadmap) item 12), this three-tier pattern extends naturally: each extracted service provisions its own `<service>_app` role scoped only to the tables it owns, following the same least-privilege template established here rather than inventing a new one.
