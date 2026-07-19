# ADR-0002: Technology Stack Selection

**Status:** Accepted
**Date:** 2026-07-19

## Context

The SRS fixes *what* the system does but deliberately excludes *how* (§1). A stack must be chosen that: supports strict layering and dependency injection (primary objective), has first-class RBAC/method-security support (§8 Security), supports the future integrations listed in §15 (JWT, OAuth2/Google login, Redis, Kafka/RabbitMQ, Docker, AWS, REST API, microservices) without rework, and is realistic for a single-team portfolio build (C2).

## Decision

| Concern | Choice | Why |
|---|---|---|
| Backend language/runtime | **Java 21 (LTS)** | Long-term support, virtual threads (Project Loom) available for future scalability work without a runtime change. |
| Backend framework | **Spring Boot 3.x** | Industry-standard for enterprise layered architecture; native support for DI, AOP (method security, transactions), profiles, and the entire package structure this document specifies (`controller`, `service`, `repository`, `security`, `config`, `validation`, `exception`). |
| Persistence | **Spring Data JPA / Hibernate** over **PostgreSQL** | JPA gives repository-pattern persistence out of the box, matching ADR-0001's layering. PostgreSQL chosen over MySQL for richer indexing (partial/expression indexes, `GIN`/full-text search for SRS §7.8 search requirements), strong `JSON`/`JSONB` support (useful for the audit/event-metadata payloads in the Audit module without schema churn), and native support for row-level optimistic locking semantics used with `@Version` (ADR-0010). |
| API style | **REST (JSON) over HTTPS** | Directly satisfies SRS §15's "formal REST API surface for third-party or mobile consumption" future item; GraphQL is not justified at current scope (see Alternatives). |
| Frontend | **React 18 + TypeScript**, consuming the REST API as a decoupled SPA | Decoupling frontend from backend (rather than server-rendered templates) is what makes JWT-based stateless auth (ADR-0003), a future native mobile client, and a future SPA-to-microservices migration (SRS §15) require no backend rework. |
| Build tooling | **Maven** (backend), **Vite** (frontend) | Maven's declarative dependency management fits an enterprise, multi-module-capable build; Vite gives fast SPA dev/build cycles. |
| API documentation | **springdoc-openapi (OpenAPI 3 / Swagger UI)** | Auto-generates the contract described in [04-API-Design.md](../04-API-Design.md) directly from controller annotations, keeping documentation from drifting out of sync with code. |
| Object mapping | **MapStruct** | Compile-time-generated DTO↔Entity mappers — see ADR-0009. |

## Consequences

- **Positive:** Every future-scope item in SRS §15 has a direct, well-trodden integration path in this stack: Spring Security supports JWT and OAuth2/Google login as configuration, not rearchitecture; Spring's caching abstraction (`@Cacheable`) drops in Redis with a starter dependency; Spring for Apache Kafka / RabbitMQ integrate through the existing event/listener package (see [02-Architecture.md](../02-Architecture.md#event-package)); the whole application is a single JAR, trivially containerized (ADR referenced in roadmap).
- **Negative:** Java/Spring has more ceremony (annotations, configuration) than a minimal framework — accepted deliberately per the brief's instruction to prioritize maintainability over shorter code.
- **Alternatives considered:**
  - *Node.js/NestJS backend* — also viable (NestJS mirrors this layered/DI style) but Spring Security's method-level RBAC and audit/versioning ecosystem (Hibernate Envers-compatible patterns) is more mature for this requirement set.
  - *GraphQL API* — rejected for v1: SRS list/filter/sort/paginate requirements (§7.8–7.10) are naturally REST resource operations; GraphQL's main advantage (flexible client-driven queries) is not a stated requirement, and it would complicate the RBAC-per-field story. Not precluded later — GraphQL can be added as an additional interface layer beside REST without touching the service layer.
  - *Server-rendered monolith (Thymeleaf)* — rejected: blocks the SPA/mobile/decoupled-API future-scope items without a full rewrite.
