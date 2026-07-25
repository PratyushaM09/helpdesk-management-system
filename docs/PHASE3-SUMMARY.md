# Phase 3 Project Summary — Ticket Management

**Status:** Complete. All milestones (Design Review, Entity Layer, DTO Layer, Mapper Layer, Service Layer, Controller Layer, Unit Testing, Integration Testing, Swagger/OpenAPI Review, Manual End-to-End Testing) finished and reviewed. `mvnw clean verify`: **BUILD SUCCESS, 449/449 tests passing.**

This document is the reference point before Phase 4 begins. It describes what was actually built, not what was planned — every claim below is backed by the Phase 3 codebase as it stands today.

---

## Phase 3 Overview

**Goal.** Extend the HelpDesk backend (Phase 1: JWT auth/RBAC; Phase 2: account management) with the core support-ticket domain: tickets, categories, comments, attachments, and an append-only activity history — the actual product the rest of the system exists to serve.

**Modules implemented.**
- `ticket` — `Ticket`, `Category`, ticket-number sequencing, and the full ticket lifecycle
- `comment` — public and internal ticket comments
- `attachment` — file-metadata records scoped to a ticket or a comment
- `tickethistory` — append-only audit trail of ticket-affecting actions

**Overall completion status.** Feature-complete for the scope defined at Design Review. Design, entities, repositories, DTOs, mappers, services, controllers, unit tests, integration tests, Swagger documentation, and manual end-to-end verification are all done. Six real runtime bugs were found during manual testing and fixed (see **Testing** below); the suite is green.

---

## Architecture

Phase 3 slots into the existing modular-monolith / layered architecture (ADR-0001, ADR-0011) without introducing a new architectural style — each module is a vertical slice (`entity/`, `repository/`, `dto/`, `mapper/`, `service/`, `controller/`) inside its own top-level package, exactly like the Phase 1/2 `auth`, `account`, `user`, and `role` modules.

### Domain shape and relationships

```
Category (1) ──< (many) Ticket
User (creator) (1) ──< (many) Ticket
User (assignee, nullable) (1) ──< (many) Ticket
Ticket (1) ──< (many) Comment
Ticket (1) ──< (many) Attachment   (comment_id nullable → ticket-level attachment)
Comment (1) ──< (many) Attachment (when comment-scoped)
Ticket (1) ──< (many) TicketHistory
User (actor) (1) ──< (many) TicketHistory
```

- **Category** is a first-class entity (not an enum) with `active` boolean and admin-managed lifecycle — new categories or deactivating a stale one requires no code change.
- **Ticket** is the aggregate root. It intentionally declares **no `@OneToMany` children fields** (comments/attachments/history are always queried from their own repository, filtered/paginated, never eagerly loaded off the ticket) — this avoids N+1 and unbounded-collection risk on the hottest entity in the system.
- **Comment** carries a `visibility` (`PUBLIC`/`INTERNAL`) that every other read path (comment listing, attachment listing, history listing) must respect.
- **Attachment** is metadata-only (see **Design Decisions**) and belongs to *either* a ticket directly *or* a comment (`ticket` is always set — derived from the comment when comment-scoped — `comment` is nullable).
- **TicketHistory** is a pure audit log: append-only, one row per ticket-affecting action, with an `internal` flag mirroring the visibility of whatever it refers to.

### Layered architecture

Each module follows the same six-layer shape, and a request always flows through them in this order:

```
Controller → Service → Repository → Entity
                ↓
              Mapper (Entity ⇄ DTO, at the Service boundary)
```

- **Entity** — JPA-mapped domain object. Never leaves the Service layer.
- **Repository** — `JpaRepository`-based (Spring Data derived queries plus a small number of explicit `@Query`s), no business logic.
- **DTO** — request/response records; the only shape a Controller ever sees or returns.
- **Mapper** — hand-written interface + `*Impl` class (see **Design Decisions** — MapStruct was aspirational, never wired in). Converts Entity → response DTO and applies request DTO fields onto an Entity.
- **Service** — the only layer that makes decisions: ownership/visibility checks, workflow-transition legality, ticket-number generation, history writing, transaction boundaries. Interfaces are **DTO/void-only** — no method returns or accepts an entity, even for internal cross-module calls (e.g. `TicketService.validateTicketAccess(Long)` is `void`, not entity-returning).
- **Controller** — binds the HTTP request, applies `@Valid`, delegates to the Service exactly once, shapes the `ApiResponse`/`PageResponse` envelope. Carries only a coarse `@PreAuthorize` role gate; it never contains a business rule.

Business logic therefore flows in one direction only: a Controller method never branches on business state, and a Repository never enforces a rule beyond what SQL/JPQL expresses structurally (e.g. `TicketHistoryRepository`'s missing update/delete methods enforce append-only *by omission*, not by convention).

---

## Features Implemented

### Ticket Management
- **Create** — `POST /tickets` (USER only); auto-generates a concurrency-safe ticket number, starts at `OPEN`, unassigned.
- **Update** — `PUT /tickets/{id}`; title/description only, creator (while `OPEN`/`WAITING_FOR_CUSTOMER`) or admin (any time).
- **Assign** — `POST /tickets/{id}/assign`; admin-only, first assignment from `OPEN`.
- **Reassign** — `POST /tickets/{id}/reassign`; admin-only, from any active-assigned status.
- **Status workflow** — `POST /tickets/{id}/status`; a fixed legal-transition table (below), enforced independent of the caller's role permissions.
- **Reopen** — `POST /tickets/{id}/reopen`; from `RESOLVED` only, back to `ASSIGNED`; creator within a 7-day window, admin any time.
- **Soft delete** — `DELETE /tickets/{id}`; admin-only, `deleted_at`/`deleted_by` set, never a hard delete, invisible to all normal queries afterward (`@SQLRestriction`).
- **Optimistic locking** — every mutating ticket endpoint requires the caller's last-known `version`; a stale value is rejected as a `409`, never silently overwritten.

**Status lifecycle** (`OPEN → ASSIGNED → IN_PROGRESS ⇄ WAITING_FOR_CUSTOMER → RESOLVED → CLOSED`, with `RESOLVED → ASSIGNED` via Reopen): `OPEN` and `CLOSED` are deliberately absent as *keys* in the transition table — no button leads out of `CLOSED` except a fresh reopen from `RESOLVED`, and `OPEN` only ever advances via explicit Assign, not `status`.

### Category
- **Seeded categories** — 8 defaults (`Software`, `Hardware`, `Network`, `Email`, `Accounts`, `Security`, `Infrastructure`, `Other`) via a `@Order(3)` seeder.
- **Active validation** — a ticket can only be created against an `active` category; deactivating a category never affects tickets already using it.

### Comments
- **Public comments** — visible to the ticket's creator, assigned engineer, and admins.
- **Internal notes** — staff-only (`SUPPORT_ENGINEER`/`ADMIN`); a `USER` attempting to post one is rejected.
- **Edit/delete** — author or admin only; edit sets `edited=true`/`editedAt`.
- **Visibility rules** — a `USER` caller never receives an `INTERNAL` comment in any listing.

### Attachments
- **Upload** — ticket-scoped (`POST /tickets/{ticketId}/attachments`) or comment-scoped (`POST /comments/{commentId}/attachments`); multipart, single `file` part.
- **Download** — metadata retrieval (`GET /attachments/{id}`); no file-byte streaming (no `FileStorageService` exists yet — ADR-0008 is a placeholder, a random UUID stands in for a real storage key).
- **Delete** — uploader or admin only.
- **Validation** — MIME allow-list (JPEG/PNG, PDF, Word, Excel, ZIP) and a 10 MB size cap, both enforced in the Service layer; the servlet container's own multipart limits (1 MB/file, 10 MB/request, Spring Boot defaults) are enforced ahead of that and now map to a proper `413`.
- **Metadata storage** — `Attachment` never stores file bytes, only filename/MIME/size/storage-key/uploader.
- **Ticket-level vs comment-level** — a comment-scoped attachment's visibility follows its parent comment; a `USER` caller never sees an attachment that hangs off an `INTERNAL` comment, even in the ticket's overall attachment list.

### Ticket History
- **Append-only design** — `TicketHistoryRepository extends Repository<T, ID>` (the bare marker interface, not `JpaRepository`), exposing only `save` and two `findBy...` methods — no update or delete method exists anywhere in the type, so the append-only rule is structural, not conventional.
- **Action tracking** — `ASSIGNED`, `REASSIGNED`, `STATUS_CHANGED`, `REOPENED`, `SOFT_DELETED`, `TITLE_UPDATED`, `DESCRIPTION_UPDATED` are all live and firing. (`CREATED`, `PRIORITY_CHANGED`, `CATEGORY_CHANGED`, `COMMENT_ADDED` are defined in the `TicketHistoryAction` enum but not currently wired to any code path — see **Readiness Assessment**.)
- **Visibility filtering** — a `USER` caller receives only non-`internal` rows; staff/admin receive the full history.

---

## Security

- **Authentication** — unchanged from Phase 1/2: stateless JWT in an `HttpOnly`/`Secure` cookie, validated per-request; every Phase 3 endpoint requires it (no anonymous ticket/comment/attachment/history route exists).
- **Authorization — two layers** (SDR-004):
  - *Method security* — a coarse `@PreAuthorize` role gate on each Controller method (e.g. `hasRole('ADMIN')` on assign/reassign/delete; `hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')` where every role may attempt the call).
  - *Service-layer ownership checks* — the fine-grained rule underneath: is this `USER` the ticket's creator, is this `SUPPORT_ENGINEER` the assigned engineer, is this transition legal from the ticket's current status. The Controller's role gate is deliberately coarser than what the Service actually permits (e.g. `changeStatus` admits all three roles at the Controller because the Service alone decides *which* transition each role may request).
- **RBAC** — three roles: `USER`, `SUPPORT_ENGINEER`, `ADMIN`. No `PermissionEvaluator`/custom SpEL — plain `hasRole`/`hasAnyRole` throughout, matching the existing Phase 1/2 convention.
- **404-vs-403 anti-enumeration** — a resource that exists but isn't visible to the caller returns `404 RESOURCE_NOT_FOUND`, never `403 FORBIDDEN`; visibility is always checked *before* any ownership/ability check. (A real gap here — attachment delete skipping the visibility check — was found and fixed during manual testing; see **Testing**.)
- **CSRF** — double-submit cookie pattern unchanged from Phase 1: every mutating Phase 3 endpoint requires the `X-CSRF-Token` header to match the `csrf_token` cookie.
- **Optimistic locking** — `@Version` is scoped to `Ticket` only (not `Comment`/`Attachment`/`TicketHistory`) per ADR-0010; every ticket-mutating response now reflects the *post-flush* version (a real staleness bug here was found and fixed — see **Testing**), so a client can chain calls using the version a response just gave it.

---

## API Summary

| Controller | Base path | Responsibility |
|---|---|---|
| `TicketController` | `/api/v1/tickets` | Create, get, list, update, assign, reassign, change status, reopen, soft-delete a ticket |
| `CategoryController` | `/api/v1/categories` | List active categories (read-only, any authenticated role) |
| `CommentController` | `/api/v1/tickets/{ticketId}/comments`, `/api/v1/comments/{commentId}` | Add, list, edit, delete comments |
| `AttachmentController` | `/api/v1/tickets/{ticketId}/attachments`, `/api/v1/comments/{commentId}/attachments`, `/api/v1/attachments/{id}` | Upload (ticket- or comment-scoped), list, get metadata, delete |
| `TicketHistoryController` | `/api/v1/tickets/{ticketId}/history` | Read-only, paginated activity timeline |

20 endpoints total across these five controllers. Every list endpoint supports pagination (`page`/`size`) and, where meaningful, `sort`; every mutating endpoint returns the updated resource wrapped in `ApiResponse`; every creating endpoint returns `201` with a `Location` header pointing at the new resource.

---

## Validation

- **Bean Validation** on every request DTO (`@NotBlank`, `@NotNull`, `@Size(max = 200)` on titles, etc.) — a failure returns `400 VALIDATION_FAILED` with a field-level `validationErrors` list (field, sanitized rejected value, message).
- **Service-layer validation** for rules Bean Validation can't express: category must exist *and* be active; an assign/reassign target must exist *and* hold the `SUPPORT_ENGINEER` role; a status transition must be legal from the ticket's current status; a version must match the entity's current one; a reopen must be within the creator's 7-day window.
- **Multipart validation** — MIME allow-list and 10 MB size cap in `AttachmentServiceImpl`, ahead of which the servlet container's own limits now also resolve cleanly to `400`/`413` instead of a raw `500` (see **Exception Handling**).
- **Path/query validation** — sortable fields are checked against an explicit allow-list (`SortValidator`) so a client can never request a sort on a column that doesn't exist or shouldn't be exposed; a malformed path variable (e.g. a non-numeric ticket id) now resolves to a proper `400` (fixed during Phase 3 manual testing).

---

## Exception Handling

Unchanged single-responsibility model from Phase 1 (`GlobalExceptionHandler`, `02-Architecture.md` §12): no Controller ever builds its own error body. Handler tiers, most-specific-first:

1. `ApplicationException` (and its subtypes `BadRequestException`, `ConflictException`, `ForbiddenException`, `ResourceNotFoundException`, `UnauthorizedException`, `LockedException`, `InvalidTokenException`) — the exception itself already carries the correct status/errorCode/message.
2. `AccessDeniedException` / `AuthenticationException` — `@PreAuthorize` denials and the (currently unreachable, defensive) authentication-inside-dispatch case.
3. `MethodArgumentNotValidException` / `BindException` / `ConstraintViolationException` — Bean Validation and method-level validation, both mapped to the same field-level `ValidationError` shape.
4. `HttpMessageNotReadableException` — unparseable request body.
5. **`MethodArgumentTypeMismatchException`** *(added Phase 3 manual testing)* — a path/query value that couldn't convert to its target type (e.g. `/tickets/not-a-number`); now `400` with a `ValidationError`, previously fell through to a raw `500`.
6. **`MissingServletRequestPartException`** *(added Phase 3 manual testing)* — a multipart request missing its required `file` part; now `400`, previously a raw `500`.
7. **`MaxUploadSizeExceededException`** *(added Phase 3 Swagger milestone)* — an upload exceeding the servlet container's own size limit; now `413 FILE_TOO_LARGE`, previously a raw `500`.
8. `RuntimeException` / `Exception` — the true catch-all; full detail logged server-side only, a fixed generic message returned to the client.

All three additions share the same root cause: each is thrown by Spring MVC while resolving a controller method's arguments, *before* the controller body runs — a place no earlier phase's code had a reachable failure path, so no handler existed until Phase 3 manual testing exercised it for the first time.

---

## Testing

**Total automated tests (whole backend, current): 449 passing, 0 failures, 0 errors** (`mvnw clean verify` — BUILD SUCCESS). Phase 3 modules alone account for 234 of those.

### Unit Testing
Every Service has a corresponding `*ServiceImplTest`: `CategoryServiceImplTest`, `TicketServiceImplTest` (72 tests — the largest single class in the project, covering the full status-transition matrix, ownership rules, and the reopen window), `CommentServiceImplTest`, `AttachmentServiceImplTest`, `TicketHistoryServiceImplTest`. Convention: plain `Mockito.mock()` on collaborators (no `@Mock`/`@InjectMocks` annotations anywhere in this codebase), real domain objects (`User`, `Ticket`, `Comment`, etc.) so the test proves how the Service actually mutates them.

### Integration Testing
`*ControllerIntegrationTest` per controller (`TicketControllerIntegrationTest` — 49 tests, `CategoryControllerIntegrationTest`, `CommentControllerIntegrationTest`, `AttachmentControllerIntegrationTest`, `TicketHistoryControllerIntegrationTest`): `@SpringBootTest` + real `MockMvc` + the real security filter chain + a real H2 database, every test authenticating through the actual `POST /auth/login` endpoint rather than `@WithMockUser`.

### Manual End-to-End Testing
The app was run as a live process against a real database and driven exactly as an HTTP client would (curl + direct Swagger/OpenAPI inspection), covering: full auth lifecycle including account lockout and token expiry, the complete ticket lifecycle end-to-end, comment/attachment visibility filtering, the full authorization matrix across all three roles, and representative validation failures.

**Bugs discovered and fixed during manual testing** (none of these were catchable by the unit or integration suite as it existed — see each item's note):
1. **Account lockout was completely non-functional** — the failed-attempt counter write was silently rolled back by Spring's default rollback-on-exception rule, since `login()` always ends its failure path by throwing. Invisible to unit tests (no real transaction manager in play) and to `@Transactional` integration tests (which mask exactly this class of bug via shared-session read-your-own-writes).
2. **Stolen-refresh-token family revocation was completely non-functional** — identical root cause in the replay-detection path of `refresh()`.
3. **Mutating ticket endpoints returned a stale `version`** — the response was built before Hibernate's `@Version` increment had flushed, so a client chaining calls with the version a response just gave it would hit a spurious `409`.
4. **Attachment delete leaked resource existence** — skipped the visibility check every sibling attachment endpoint already had, so a caller with zero legitimate access to the parent ticket got `403` (not `404`) on delete.
5. **A non-numeric path variable produced a raw `500`** instead of `400`.
6. **A multipart request missing its file part produced a raw `500`** instead of `400`.

**Production fixes made:** a new `AuthSecurityEventRecorder` collaborator (runs the two security writes above in their own `REQUIRES_NEW` transaction, so they survive the enclosing method's rollback); `ticketRepository.save(...)` → `saveAndFlush(...)` at the five affected call sites; a `ticketService.validateTicketAccess(...)` check added to `AttachmentServiceImpl.deleteAttachment`; two new `@ExceptionHandler` methods in `GlobalExceptionHandler`. Every fix was empirically re-verified against the live app, and the automated suite (including new/updated tests for each fix) is fully green.

---

## Design Decisions

- **Category as an entity, not an enum** — categories are admin-managed at runtime (add/deactivate) without a code deployment; an enum would require one.
- **Append-only `TicketHistory`** (ADR-0006) — enforced structurally, not by convention: the repository interface itself has no update/delete method (`extends Repository<T, ID>`, not `JpaRepository`), so a future developer physically cannot wire up a "correct this history row" feature without first changing the repository's supertype.
- **Optimistic locking scoped to `Ticket` only** (ADR-0010) — comments and attachments are smaller, append-adjacent records where a lost-update race is far less consequential than on the ticket's own workflow-critical fields (`status`, `assignedTo`).
- **Service-layer ownership checks, not a `PermissionEvaluator`** — a coarse `@PreAuthorize` role gate at the Controller plus explicit `isVisibleTo`/`validateXActor` methods in the Service. Chosen over a custom SpEL `PermissionEvaluator` to keep the authorization *logic* readable as plain Java sitting next to the business rules it's protecting, rather than split across an annotation string and a separate evaluator bean.
- **Layered architecture with DTO-only Service interfaces** — no Service method, public or internal-cross-module, ever accepts or returns an entity (`TicketService.validateTicketAccess(Long)` is `void`); a caller that needs the entity re-resolves it via its own module's repository. This keeps every module's persistence model genuinely private.
- **Metadata-only attachments** — no `FileStorageService` exists yet (ADR-0008 names the abstraction but nothing implements it); `Attachment` stores filename/MIME/size/storage-key only, with the Controller generating a placeholder UUID storage key. Real byte storage is explicitly future work.
- **Ticket numbering strategy** — `HD-{year}-{6-digit sequence}` (e.g. `HD-2026-000042`), generated via a dedicated `TicketSequence` table keyed by year, incremented under `@Lock(LockModeType.PESSIMISTIC_WRITE)` rather than a database-native `INSERT ... ON DUPLICATE KEY`/sequence object, chosen because this codebase has no other native-SQL usage anywhere and pessimistic locking keeps the whole codebase in one dialect-portable idiom.
- **Hand-written mappers, not MapStruct** — ADR-0009 names MapStruct as the aspiration, but no Phase 1–3 module actually wires up the annotation processor; every mapper in the codebase, Phase 3 included, is a plain interface plus a hand-written `*Impl` class. Documented here so Phase 4 doesn't assume otherwise.

---

## Final Statistics

*(Whole backend, current state — Phases 1–3 combined)*

| Category | Count |
|---|---|
| Modules | 9 (`auth`, `account`, `role`, `user`, `ticket`, `comment`, `attachment`, `tickethistory`, plus shared `common`/`config`/`security`/`exception`) |
| Controllers | 10 |
| Service interfaces | 12 |
| Repositories | 11 |
| Entities (`@Entity`) | 11 |
| Enums | 6 |
| DTOs | 27 |
| Mapper interfaces (+ hand-written impls) | 8 |
| Total automated tests | 449 (all passing) |

*Phase 3 alone contributed: 4 modules, 5 controllers, 5 service interfaces, 6 repositories, 6 entities, 4 enums, 13 DTOs, 5 mappers, 234 tests.*

---

## Readiness Assessment

**Completed phases:**
- Phase 1 — JWT authentication, RBAC, CSRF
- Phase 2 — Account management (profile, password reset, email verification)
- Phase 3 — Ticket Management (this document)

**Remaining backend phases:** Phase 4 onward — not yet scoped in this codebase. Known, explicitly-flagged open items to carry into that scoping:
- No real `FileStorageService` (ADR-0008) — attachments are metadata-only today.
- `TicketHistoryAction.CREATED`/`PRIORITY_CHANGED`/`CATEGORY_CHANGED`/`COMMENT_ADDED` are defined but unused — worth a decision (wire them up, or remove the unused constants) before more consumers start depending on history completeness.
- Two minor user-facing message inconsistencies noted but deliberately left unfixed pending a product decision: `CommentServiceImpl`'s 403 message still says "Customers" (this system has no `CUSTOMER` role); `AccountServiceImpl.verifyEmail`'s failure message says "reset link" (copied from the password-reset flow).
- No priority/category change capability exists after ticket creation — by design (per `UpdateTicketRequest`'s own scope), but worth confirming Phase 4 doesn't need it.

**Current estimated backend completion:** roughly 60–65% of a full production backend — core auth/account/ticket domain is solid and tested, but file storage, notifications (only a logging stub exists — ADR-0007), rate limiting (SDR-013, not yet implemented), and any reporting/dashboard capability are all still ahead.

---

## Next Phase

Phase 4 scope is not yet defined in this codebase, but the most natural next steps given what Phase 3 leaves open are:

1. **File storage** — implement the `FileStorageService` abstraction ADR-0008 already names, replacing the placeholder UUID storage key with a real backend (local disk or object storage) and wiring actual byte upload/download through `AttachmentController`.
2. **Notifications** — replace `LoggingNotificationServiceImpl` with a real channel (email at minimum), now that there are real events worth notifying about (ticket assigned, status changed, comment added).
3. **Rate limiting** — SDR-013 is designed but not implemented; the ticket-creation and comment endpoints are the first realistic abuse targets now that they're live.
4. **Reporting/search** — the ticket-history and ticket-listing groundwork (pagination, sorting, role-scoped visibility) is already in place; a dashboard or search capability would build directly on it rather than needing new infrastructure.
5. **Decide on the four unused `TicketHistoryAction` values** before any of the above adds new consumers of the history feed.
