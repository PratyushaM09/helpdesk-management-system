# ADR-0009: Strict DTO/Entity Separation via MapStruct

**Status:** Accepted
**Date:** 2026-07-19

## Context

JPA entities carry persistence concerns (lazy-loading proxies, bidirectional relationship references, `@Version` fields) that are unsafe and inappropriate to serialize directly to a JSON API response — doing so risks accidental exposure of internal fields (e.g., a User entity's password hash), lazy-initialization exceptions outside a transaction, and tight coupling between the wire contract and the schema (a column rename becomes an API break).

## Decision

Entities never cross the Controller boundary in either direction. Every controller method accepts a request DTO (validated, [see Validation Strategy](../02-Architecture.md#validation-strategy)) and returns a response DTO. Mapping between DTO and Entity is generated at compile time by **MapStruct**, with mapper interfaces living in the `mapper` package, one per module, explicitly listing included/excluded fields (so a new sensitive entity field is not accidentally serialized by default — mappers are allow-listed, not reflection-based).

## Consequences

- **Positive:** API response shape is decoupled from schema shape — a database refactor (e.g., splitting a `full_name` column into `first_name`/`last_name`) requires a mapper change, not necessarily an API contract change, satisfying API versioning stability ([04-API-Design.md](../04-API-Design.md)).
- **Positive:** Compile-time-generated mapping code (vs. reflection-based mappers like ModelMapper) is faster and, critically, fails the *build* if a mapping is incomplete/ambiguous, catching mistakes before runtime.
- **Positive:** Sensitive fields (password hash, internal flags) are structurally impossible to leak through a response DTO that simply never declares them.
- **Negative:** More classes (one DTO pair + one mapper per entity, roughly) than returning entities directly — accepted deliberately; this is the "maintainability over shorter code" tradeoff the brief mandates explicitly.
- **Alternatives considered:**
  - *Serialize entities directly with `@JsonIgnore` on sensitive fields* — rejected: security-by-exception (must remember to annotate every sensitive field) instead of security-by-default (DTO must explicitly opt a field in).
  - *Manual mapper methods* — rejected: repetitive boilerplate that MapStruct eliminates at compile time with equivalent runtime performance to hand-written code.
