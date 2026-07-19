# 04 — API Design (Blueprint)

## HelpDesk Management System

| | |
|---|---|
| **Document Version** | 1.0 |
| **Status** | Accepted — Architecture Phase |
| **Style** | REST over HTTPS, JSON, URI-versioned (`/api/v1`) — ADR-0012 |
| **Related** | [02-Architecture.md](02-Architecture.md) (request lifecycle), [03-Security.md](03-Security.md) (auth/authz mechanics), ADR-0009 (DTOs only, never entities) |

This is a **contract blueprint**, not an implementation — no controller/service/SQL code. Full request/response field-level schemas are generated from the eventual DTOs into OpenAPI (springdoc, ADR-0002) and published via Swagger UI; this document fixes the *endpoint inventory, purpose, authorization, and error contract* that the generated schema must conform to.

---

## 1. Conventions Applied to Every Endpoint

- **Base path:** `/api/v1`.
- **AuthN:** unless listed as "Public," every endpoint requires a valid access-token cookie (Section 2–3 of [03-Security.md](03-Security.md)); an invalid/missing/expired token → `401`.
- **AuthZ:** enforced per the RBAC matrix ([03-Security.md §5](03-Security.md#5-rbac-matrix)); an authenticated-but-unauthorized call → `403`.
- **Pagination:** every list endpoint accepts `page` (0-based), `size` (default 20, max 100), `sort` (`field,asc|desc`) and returns a `PageResponse<T>` envelope: `{ content: T[], page, size, totalElements, totalPages }` (FR-PAGE-1).
- **Filtering:** list endpoints accept filter query params combinable via logical AND (FR-FILT-1) — see per-endpoint tables.
- **Idempotency:** `PUT`/`PATCH` are idempotent by design; `POST` creates a new resource or performs a one-time state transition (e.g., `close`).
- **Optimistic concurrency:** mutating endpoints on versioned entities (`Ticket`) require an `If-Match: <version>` header (or a `version` body field); a stale version → `409` (ADR-0010).
- **Error response format** (shared by every endpoint, superseding the per-row "Error Responses" column below with full structure):

  ```
  {
    "errorCode": "TICKET_INVALID_TRANSITION",
    "message": "This ticket can't move to Closed directly from Open.",
    "violations": [ { "field": "status", "reason": "..." } ],   // present only for 400s
    "timestamp": "2026-07-19T10:15:00Z",
    "traceId": "a1b2c3d4"
  }
  ```
  Never contains a stack trace, SQL fragment, or internal identifier (SRS Constraint C4; [02-Architecture.md §12](02-Architecture.md#12-exception-flow--strategy)).

- **Standard status codes used throughout:** `200` OK, `201` Created, `204` No Content, `400` validation, `401` unauthenticated, `403` unauthorized, `404` not found / not visible to caller, `409` conflict (state/version/uniqueness), `422` semantically invalid business request, `500` unexpected (generic message only).

---

## 2. Authentication Module

| Endpoint | Purpose | Auth | Roles | Request | Response | Validation | Errors |
|---|---|---|---|---|---|---|---|
| `POST /auth/register` | Self-register a new User (FR-AUTH-1) | Public | — | `{name, email, password}` | `201 {userId, email, status: UNVERIFIED}` | Email format/uniqueness, `@StrongPassword` (FR-AUTH-7) | `400` validation; `409` email already registered |
| `POST /auth/verify-email` | Confirm email via token (FR-AUTH-2) | Public | — | `{token}` | `200 {status: VERIFIED}` | Token signature + not expired | `400` invalid/expired token |
| `POST /auth/resend-verification` | Reissue verification token | Public | — | `{email}` | `202` (always, to avoid email enumeration) | Rate-limited (§13) | `429` too many requests |
| `POST /auth/login` | Authenticate (FR-AUTH-3) | Public | — | `{email, password}` | `200`, sets access+refresh cookies, body `{userId, role, name}` | Non-empty fields | `401` bad credentials; `423` account locked (§13 of [03-Security.md](03-Security.md)) |
| `POST /auth/refresh` | Rotate access token using refresh cookie | Public (refresh cookie required) | — | *(none — cookie only)* | `200`, new access+refresh cookies | Refresh token valid, not revoked, not reused | `401` invalid/expired/reused refresh token |
| `POST /auth/logout` | Invalidate active session (FR-AUTH-4) | Authenticated | any | *(none)* | `204`, clears cookies | — | `401` if already unauthenticated |
| `POST /auth/forgot-password` | Request reset link (FR-AUTH-5) | Public | — | `{email}` | `202` (always) | Rate-limited | `429` |
| `POST /auth/reset-password` | Complete reset (FR-AUTH-5) | Public | — | `{token, newPassword}` | `200` | Token valid; `@StrongPassword` | `400` invalid/expired token or weak password |
| `POST /auth/change-password` | Change password (FR-AUTH-6) | Authenticated | any | `{currentPassword, newPassword}` | `204` | Current password must verify; `@StrongPassword` | `401` current password wrong; `400` weak password |

---

## 3. User / Profile Module

| Endpoint | Purpose | Auth | Roles | Request | Response | Validation | Errors |
|---|---|---|---|---|---|---|---|
| `GET /users/me` | Get own profile (FR-PROF-1) | Authenticated | any | — | `200 UserProfileDto` | — | `401` |
| `PUT /users/me` | Update own profile | Authenticated | any | `{name, phone, ...}` (never `role`, `email` may require re-verification) | `200 UserProfileDto` | Field-level Bean Validation | `400` |
| `POST /users/me/avatar` | Upload avatar | Authenticated | any | `multipart/form-data` | `200 {avatarUrl}` | Image type/size allow-list ([03-Security.md §12](03-Security.md#12-file-upload-security)) | `400` invalid file |
| `GET /admin/users` | List/search all users | Authenticated | ADMIN | Query: `role`, `status`, `search`, paging | `200 PageResponse<UserSummaryDto>` | — | `403` |
| `POST /admin/users` | Provision a Support Engineer/Admin account (Assumption A1) | Authenticated | ADMIN | `{name, email, role}` (temp password issued via reset flow) | `201 UserSummaryDto` | `role` ∈ {SUPPORT_ENGINEER, ADMIN}; email unique | `400`; `409` |
| `PUT /admin/users/{id}/role` | Change a user's role | Authenticated | ADMIN | `{role}` | `200 UserSummaryDto` | Cannot demote the last remaining Admin | `422` |
| `PATCH /admin/users/{id}/status` | Activate/deactivate a user | Authenticated | ADMIN | `{active: boolean}` | `200 UserSummaryDto` | Cannot deactivate self | `422` |

---

## 4. Ticket Module

| Endpoint | Purpose | Auth | Roles | Request | Response | Validation | Errors |
|---|---|---|---|---|---|---|---|
| `POST /tickets` | Create ticket (FR-TICK-3, UC-1) | Authenticated | USER | `{title, description, categoryId, priority}` | `201 TicketDto` (status `OPEN`, generated ticket number FR-TICK-1) | Required fields; `categoryId` must be active | `400`; `404` category not found |
| `GET /tickets` | List/search/filter/sort/paginate (FR-TICK-9/10, FR-SRCH-1, FR-FILT-1) | Authenticated | any (role-scoped, §5 of [03-Security.md](03-Security.md)) | Query: `status`, `priority`, `categoryId`, `assignedEngineerId`, `q` (text), `dateFrom`, `dateTo`, paging/sort | `200 PageResponse<TicketSummaryDto>` | Combinable filters (FR-FILT-1) | `400` bad filter value |
| `GET /tickets/{id}` | Get ticket detail | Authenticated | creator / assigned engineer / ADMIN | — | `200 TicketDetailDto` (includes current state; activity/comments fetched separately) | Visibility check (FR-SRCH-2) | `404` (not found *or* not visible — never distinguishes, to avoid leaking existence) |
| `PUT /tickets/{id}` | Edit description/attachments while editable (FR-TICK-4) | Authenticated | creator (own ticket) | `{description}` | `200 TicketDto` | Only while status ∈ {OPEN, WAITING_FOR_USER}; `category`/`priority` not editable here (Assumption A3) | `409` not in editable state |
| `PATCH /tickets/{id}/status` | Transition status (FR-FLOW-1/2) | Authenticated | assigned engineer / ADMIN | `{targetStatus, comment?}` + `If-Match` version | `200 TicketDto` | Must be a legal transition per SRS §10 workflow | `409` illegal transition or version conflict |
| `PATCH /tickets/{id}/assign` | Assign/reassign (FR-TICK-8, UC-2) | Authenticated | ADMIN (any); SUPPORT_ENGINEER (self-assign, where enabled) | `{engineerId}` + `If-Match` | `200 TicketDto` (→ `ASSIGNED`) | `engineerId` must be an active engineer | `422` engineer inactive; `409` version conflict |
| `PATCH /tickets/{id}/priority` | Re-triage priority/category | Authenticated | assigned engineer / ADMIN | `{priority, categoryId}` | `200 TicketDto` | Allowed only pre-resolution (Assumption A3) | `409` |
| `POST /tickets/{id}/close` | User confirms resolution (FR-TICK-6, UC-4) | Authenticated | creator | `{satisfactionRating?}` (SRS §17.4 hook, optional) | `200 TicketDto` (→ `CLOSED`) | Must be `RESOLVED` | `409` |
| `POST /tickets/{id}/reopen` | Reopen within window (FR-TICK-7, UC-4) | Authenticated | creator | `{reason}` | `200 TicketDto` (→ `REOPENED` → `ASSIGNED`) | Must be `CLOSED` and within reopen window (Assumption A5) | `409` outside window or not closed |
| `POST /tickets/{id}/escalate` | Escalate (FR ticket flow, UC-5) | Authenticated | assigned engineer | `{reason}` | `200 TicketDto`, notifies Admins | Must be assigned to caller | `403`/`409` |
| `DELETE /tickets/{id}` | Soft-delete (FR-TICK-5, ADR-0005) | Authenticated | ADMIN | `{reason}` | `204` | — | `403` |
| `GET /tickets/{id}/activity` | Full immutable timeline (FR-TICK-12) | Authenticated | creator / assigned engineer / ADMIN | Paging | `200 PageResponse<TicketActivityDto>` | Same visibility check as `GET /tickets/{id}` | `404` |
| `GET /tickets/export` | CSV export of filtered list (FR-TICK-11) | Authenticated | any (role-scoped) | Same query params as `GET /tickets` | `200 text/csv` (streamed) | Result set capped (configurable max rows) to bound response size | `400` |

---

## 5. Comment Module

| Endpoint | Purpose | Auth | Roles | Request | Response | Validation | Errors |
|---|---|---|---|---|---|---|---|
| `GET /tickets/{ticketId}/comments` | List thread (FR-COM-1) | Authenticated | ticket-visible parties | Paging | `200 PageResponse<CommentDto>` | Ticket visibility | `404` |
| `POST /tickets/{ticketId}/comments` | Add comment (FR-COM-1/2, UC-7) | Authenticated | ticket-visible parties | `{content}` | `201 CommentDto` | Non-empty content, max length | `400`; `404` |
| `PUT /comments/{id}` | Edit own comment (FR-COM-3) | Authenticated | comment author | `{content}` | `200 CommentDto` (`edited: true`, `editedAt` set) | Only own comment, within an edit window | `403` not author; `409` outside edit window |

---

## 6. Attachment Module

| Endpoint | Purpose | Auth | Roles | Request | Response | Validation | Errors |
|---|---|---|---|---|---|---|---|
| `POST /tickets/{ticketId}/attachments` | Upload on ticket (FR-ATT-1) | Authenticated | ticket-visible parties | `multipart/form-data` | `201 AttachmentDto` | Type allow-list, size cap, count cap (FR-ATT-2/3) | `400`; `413` too large |
| `POST /comments/{commentId}/attachments` | Upload on comment (FR-COM-2) | Authenticated | ticket-visible parties | `multipart/form-data` | `201 AttachmentDto` | Same as above | `400`; `413` |
| `GET /attachments/{id}` | Download (FR-ATT-4) | Authenticated | parent-ticket-visible parties | — | `200` binary stream, `Content-Disposition: attachment` | Re-checks ticket visibility at request time ([03-Security.md §12](03-Security.md#12-file-upload-security)) | `403`/`404` |
| `DELETE /attachments/{id}` | Remove own upload | Authenticated | uploader / ADMIN | — | `204` | — | `403` |

---

## 7. Notification Module

| Endpoint | Purpose | Auth | Roles | Request | Response | Validation | Errors |
|---|---|---|---|---|---|---|---|
| `GET /notifications` | List own notifications (FR-NOTIF-2) | Authenticated | any (own only) | Query: `unreadOnly`, paging | `200 PageResponse<NotificationDto>` | — | — |
| `GET /notifications/unread-count` | Badge count for dashboard | Authenticated | any (own only) | — | `200 {count}` | — | — |
| `PATCH /notifications/{id}/read` | Mark one read | Authenticated | owner only | — | `204` | — | `403` |
| `PATCH /notifications/read-all` | Mark all read | Authenticated | any (own only) | — | `204` | — | — |

---

## 8. Dashboard Module

| Endpoint | Purpose | Auth | Roles | Request | Response | Validation | Errors |
|---|---|---|---|---|---|---|---|
| `GET /dashboard/user` | User dashboard aggregate (FR-DASH-2) | Authenticated | USER | — | `200 UserDashboardDto {openCount, pendingCount, closedCount, recentActivity[], unreadNotifications}` | — | `403` if wrong role |
| `GET /dashboard/engineer` | Engineer dashboard aggregate (FR-DASH-3) | Authenticated | SUPPORT_ENGINEER | — | `200 EngineerDashboardDto {byStatus, byPriority, avgResolutionTime, recentlyUpdated[]}` | — | `403` |
| `GET /dashboard/admin` | Admin dashboard aggregate (FR-DASH-4) | Authenticated | ADMIN | — | `200 AdminDashboardDto {totalUsers, totalTickets, activeEngineers, statsByStatus/Category/Priority, trends[]}` | — | `403` |

---

## 9. Reports Module

| Endpoint | Purpose | Auth | Roles | Request | Response | Validation | Errors |
|---|---|---|---|---|---|---|---|
| `GET /reports/category` | Tickets-per-category report (FR-REP-1) | Authenticated | ADMIN | Query: `dateFrom`, `dateTo` | `200 CategoryReportDto[]` | Date range required, `dateFrom <= dateTo` | `400` |
| `GET /reports/resolution-time` | Avg resolution time report | Authenticated | ADMIN | Query: `dateFrom`, `dateTo`, `groupBy` | `200 ResolutionTimeReportDto[]` | as above | `400` |
| `GET /reports/engineer-performance` | Per-engineer performance | Authenticated | ADMIN | Query: `dateFrom`, `dateTo` | `200 EngineerPerformanceReportDto[]` | as above | `400` |
| `GET /reports/summary` | Weekly/monthly rollup (FR-REP-1) | Authenticated | ADMIN | Query: `period` (`weekly`/`monthly`), `date` | `200 ReportSummaryDto` | `period` ∈ allowed values | `400` |
| `GET /reports/export` | Export any of the above (FR-REP-2) | Authenticated | ADMIN | Query: `reportType` + that report's params, `format` (`csv`) | `200 text/csv` (streamed) | `reportType`/`format` valid | `400` |

---

## 10. Administration Module (Categories & Priorities)

| Endpoint | Purpose | Auth | Roles | Request | Response | Validation | Errors |
|---|---|---|---|---|---|---|---|
| `GET /categories` | List active categories (used by ticket-creation UI) | Authenticated | any | Query: `includeInactive` (ADMIN only) | `200 CategoryDto[]` | — | — |
| `POST /admin/categories` | Create category (FR-PRI-3) | Authenticated | ADMIN | `{name, description?}` | `201 CategoryDto` | Unique name | `409` |
| `PUT /admin/categories/{id}` | Rename/update category | Authenticated | ADMIN | `{name, description?}` | `200 CategoryDto` | Unique name | `409` |
| `PATCH /admin/categories/{id}/deactivate` | Deactivate without deleting (FR-PRI-3) | Authenticated | ADMIN | — | `204` | Historical tickets keep the reference (ADR-0005) | — |

---

## 11. Audit Module (Administrator-only)

| Endpoint | Purpose | Auth | Roles | Request | Response | Validation | Errors |
|---|---|---|---|---|---|---|---|
| `GET /admin/audit-log` | View administrative action log (SRS §17.6) | Authenticated | ADMIN | Query: `actorId`, `actionType`, `dateFrom`, `dateTo`, paging | `200 PageResponse<AdminAuditEntryDto>` | — | `403` |

---

## 12. Versioning & Deprecation Policy

Per ADR-0012: a breaking change to any single resource's contract ships as a new `/api/v2/<resource>` for that resource only; the corresponding `v1` route remains functional and documented (marked `Deprecated` in the OpenAPI spec, with a `Sunset` response header) for a minimum deprecation window agreed with API consumers before removal — never a silent breaking change to a published `v1` contract.
