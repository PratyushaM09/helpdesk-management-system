# Software Requirements Specification
## HelpDesk Management System

| | |
|---|---|
| **Document Version** | 1.0 |
| **Date** | 2026-07-19 |
| **Status** | Draft — Requirements Gathering Phase |
| **Prepared By** | Product & Engineering Planning |
| **Classification** | Internal / Portfolio Project |

---

## Document Control

This SRS is the single source of truth for scope during the requirements phase. No design, database schema, API contract, or code should be produced until this document is reviewed and approved. Any change to scope after approval should be tracked as a versioned amendment to this document (v1.1, v1.2, …), not a silent change during development.

---

## Table of Contents

1. Executive Summary
2. Business Objectives
3. Problem Statement
4. Stakeholders
5. User Personas
6. User Stories
7. Functional Requirements
8. Non-Functional Requirements
9. Feature Breakdown
10. Ticket Lifecycle
11. Use Case Diagrams (Textual)
12. System Scope
13. Assumptions
14. Constraints
15. Future Scope
16. Acceptance Criteria
17. Suggested Improvements Beyond Current Requirements

---

## 1. Executive Summary

The HelpDesk Management System is a centralized, web-based platform that enables organizations to capture, triage, assign, track, and resolve internal support requests — spanning software, hardware, network, account, and security issues. It replaces informal channels (email threads, chat messages, verbal requests) that currently cause lost requests, duplicated effort, and no historical accountability.

The system serves three distinct roles — **User** (requester), **Support Engineer** (resolver), and **Administrator** (platform owner) — each with a tailored dashboard and permission set. Every ticket carries a full lifecycle audit trail, threaded communication, and attachments, giving both requesters and the organization complete visibility from creation to closure.

While scoped to be buildable as a single portfolio-grade project, the system is deliberately architected around enterprise conventions — role-based access control, auditability, reporting, and a modular design that can extend toward real-world production concerns (SSO, message queues, containerized deployment) without a rewrite.

This document defines **what** the system must do and **why**. It intentionally excludes **how** (architecture, schema, APIs, code), which are subjects of subsequent design documents once this specification is approved.

---

## 2. Business Objectives

| # | Objective | Measure of Success |
|---|-----------|---------------------|
| B1 | Centralize all support requests into one system of record | Zero support requests handled outside the platform post-adoption |
| B2 | Reduce average time-to-resolution | Baseline established in first reporting cycle; target 20%+ improvement over informal process |
| B3 | Establish accountability for every ticket | 100% of tickets have an assigned owner and auditable status history |
| B4 | Give management visibility into support load and performance | Admin analytics available in real time, no manual report compilation |
| B5 | Eliminate duplicate/lost requests | Search and ticket-number tracking make duplication detectable and preventable |
| B6 | Demonstrate enterprise-grade engineering practice | System satisfies all non-functional requirements in Section 8 and passes acceptance criteria in Section 16 |

---

## 3. Problem Statement

Organizations without a dedicated helpdesk system rely on ad hoc channels — email, chat, verbal requests, or spreadsheets — to manage internal support. This produces recurring, measurable problems:

- **Lost requests**: issues raised in email or chat get buried and forgotten, with no forcing function to track them to closure.
- **No centralized tracking**: there is no single place to see what issues exist, who owns them, or what state they're in.
- **Duplicate complaints**: the same issue is reported multiple times by different people because no one can see it's already logged.
- **Poor communication**: requesters don't know if their issue was seen, who's working on it, or when to expect resolution.
- **No accountability**: without an assigned owner and a status trail, tickets stall with no visibility into why.
- **Slow resolution**: without prioritization or escalation paths, urgent issues can wait behind trivial ones.
- **No historical data**: past issues and their resolutions are not searchable, so recurring problems get re-diagnosed from scratch every time.
- **No performance metrics**: management cannot answer basic questions — how many tickets this month, which categories dominate, how engineers perform — without manual effort.

The HelpDesk Management System exists to solve these problems by providing a structured, auditable, role-aware platform that is the mandatory single channel for raising and resolving internal support issues.

---

## 4. Stakeholders

| Stakeholder | Interest / Stake |
|---|---|
| **Users (Employees/Customers)** | Need a fast, transparent way to report issues and track resolution |
| **Support Engineers** | Need a clear, prioritized queue of assigned work and the tools to resolve it |
| **Administrators** | Need control over users, categorization, assignment policy, and organizational visibility |
| **Organization / Business Owner** | Needs reduced downtime, measurable support performance, and reduced operational risk from lost issues |
| **Engineering Team (builders of this system)** | Needs an unambiguous, complete specification to design and build against |
| **Future Integrators** | Third parties (SSO providers, email systems, mobile clients) who will consume this system's extension points in later phases |

---

## 5. User Personas

### Persona 1 — "Riya," the User (Employee)
- Non-technical office employee.
- Wants to report a broken laptop or a locked account without needing to know who to email.
- Cares about: quick submission, knowing someone has seen it, being notified when it's resolved.
- Frustration today: "I emailed IT three days ago and heard nothing."

### Persona 2 — "Arjun," the Support Engineer
- Technical staff member handling 15–30 open tickets at a time.
- Wants a queue sorted by priority, clear ticket context, and a way to ask the requester for more info without a back-and-forth email chain.
- Cares about: not missing urgent tickets, having enough context to act without re-asking basic questions.
- Frustration today: "I get pinged on Slack with half the story and no history."

### Persona 3 — "Meera," the Administrator
- IT manager responsible for the support function.
- Wants to see team workload, reassign tickets when someone is out, and report resolution metrics to leadership monthly.
- Cares about: even workload distribution, SLA visibility, defensible reporting.
- Frustration today: "I have no idea if we're actually getting faster or slower at closing issues."

---

## 6. User Stories

Stories are written in the standard `As a <role>, I want <capability>, so that <benefit>` form and are grouped by role. These drive the acceptance criteria in Section 16.

### User
- As a User, I want to register and verify my email, so that my account is trusted and recoverable.
- As a User, I want to create a ticket with a category, priority, description, and attachments, so that support has full context immediately.
- As a User, I want to see all my tickets and their statuses in one place, so that I don't need to ask for updates.
- As a User, I want to comment on my own ticket, so that I can answer follow-up questions from support.
- As a User, I want to be notified when my ticket status changes, so that I don't have to keep checking manually.
- As a User, I want to reopen a closed ticket if the issue recurs, so that I don't have to file a brand-new ticket and lose history.
- As a User, I want to update my profile and password, so that I can keep my account current and secure.

### Support Engineer
- As a Support Engineer, I want to see only tickets assigned to me, sorted by priority, so that I work on the most urgent issue first.
- As a Support Engineer, I want to update ticket status and add internal progress notes, so that the requester and my manager both stay informed.
- As a Support Engineer, I want to request more information from a user without changing ticket ownership, so that the ticket doesn't stall in an ambiguous state.
- As a Support Engineer, I want to escalate a ticket I can't resolve, so that it reaches someone with the right expertise.
- As a Support Engineer, I want to see my current workload count, so that I can flag when I'm overloaded.

### Administrator
- As an Administrator, I want to view and manage all users and their roles, so that access stays correct as staff changes.
- As an Administrator, I want to assign or reassign any ticket to any engineer, so that workload stays balanced and coverage gaps (leave, attrition) are handled.
- As an Administrator, I want to define categories and priorities, so that the taxonomy matches how the organization actually classifies issues.
- As an Administrator, I want dashboards and exportable reports on ticket volume, resolution time, and engineer performance, so that I can report to leadership and identify process gaps.
- As an Administrator, I want to delete a ticket only in exceptional cases (e.g., spam, duplicate, data-entry error), so that the historical record otherwise stays complete and auditable.

---

## 7. Functional Requirements

Functional requirements are grouped by module. Each is tagged with a stable ID (`FR-<Module>-<n>`) for later traceability into design and test documents.

### 7.1 Authentication Module
- FR-AUTH-1: The system shall allow a new User to register with name, email, password, and role-appropriate default (self-registration always creates a **User**; Support Engineer and Administrator accounts are provisioned by an Administrator, not self-registered — see Section 13, Assumptions).
- FR-AUTH-2: The system shall require email verification before full account access is granted.
- FR-AUTH-3: The system shall allow login via email and password.
- FR-AUTH-4: The system shall allow logout, invalidating the active session.
- FR-AUTH-5: The system shall provide a "Forgot Password" flow that sends a time-limited reset link.
- FR-AUTH-6: The system shall allow a logged-in user to change their password after re-confirming their current password.
- FR-AUTH-7: The system shall enforce a minimum password strength policy (length, character variety).
- FR-AUTH-8: The system shall lock or throttle an account after repeated failed login attempts.

### 7.2 Dashboard Module
- FR-DASH-1: The system shall present a role-specific dashboard immediately after login, matching the user's role (User, Support Engineer, Administrator).
- FR-DASH-2: The User dashboard shall show counts of open, pending, and closed tickets, recent activity, and unread notifications.
- FR-DASH-3: The Support Engineer dashboard shall show assigned tickets grouped by status/priority, average resolution time for their own tickets, and recently updated tickets.
- FR-DASH-4: The Admin dashboard shall show total users, total tickets, active engineers, ticket statistics by status/category/priority, trend graphs, and monthly report summaries.

### 7.3 Ticket Module
- FR-TICK-1: The system shall auto-generate a unique, human-readable ticket number on creation (e.g., `HD-2026-000123`).
- FR-TICK-2: A ticket shall capture: title, description, category, priority, status, creator, assigned engineer, attachments, created date, last-updated date, resolved date, comments, and a full activity timeline.
- FR-TICK-3: A User shall be able to create a ticket, selecting category and priority, and optionally attaching files.
- FR-TICK-4: A User shall be able to update a ticket they created only while it is in a status that permits edits (Open, Waiting for User); fields subject to edit are limited to description clarifications and attachments, not category/priority once triaged (see Assumptions, Section 13).
- FR-TICK-5: Only an Administrator shall be able to permanently delete a ticket; deletion shall be a soft delete (retained for audit, hidden from normal views) — see Section 13.
- FR-TICK-6: A User may close their own ticket once it is Resolved, confirming satisfactory resolution.
- FR-TICK-7: A User may reopen a Closed ticket within a defined window (e.g., 7 days) if the issue recurs; reopening returns it to the queue with full prior history intact.
- FR-TICK-8: An Administrator (and, for their own queue, a Support Engineer via self-assignment where enabled) shall be able to assign or reassign a ticket to a Support Engineer.
- FR-TICK-9: The system shall support full-text and structured search across tickets (see Section 7.8).
- FR-TICK-10: The system shall support filtering, sorting, and pagination on all ticket list views (see Sections 7.9–7.10).
- FR-TICK-11: The system shall support exporting a filtered ticket list (e.g., to CSV) for offline reporting.
- FR-TICK-12: Every state-changing action on a ticket (status change, assignment, priority change, comment) shall be recorded in an immutable activity timeline visible to authorized viewers of that ticket.

### 7.4 Ticket Status Workflow
- FR-FLOW-1: The system shall enforce the ticket status lifecycle defined in Section 10, disallowing invalid transitions (e.g., a ticket cannot move directly from Open to Closed without passing through Resolved).
- FR-FLOW-2: Only a Support Engineer or Administrator may transition a ticket into Assigned, In Progress, Waiting for User, or Resolved.
- FR-FLOW-3: A ticket in Waiting for User status that receives no user response within a configurable period shall be flagged (visually and via notification) for follow-up — see Section 17 for auto-escalation as a suggested enhancement.

### 7.5 Priority & Category Management
- FR-PRI-1: The system shall support four priority levels: Low, Medium, High, Urgent.
- FR-PRI-2: The system shall support a configurable category list, seeded with: Software, Hardware, Network, Email, Accounts, Security, Infrastructure, Other.
- FR-PRI-3: An Administrator shall be able to add, rename, or deactivate categories without deleting historical tickets that reference them.

### 7.6 Comments Module
- FR-COM-1: Each ticket shall support a threaded list of comments authored by the ticket creator, assigned engineer, or an Administrator.
- FR-COM-2: A comment shall support text content and optional attachments.
- FR-COM-3: A comment shall record its author and creation timestamp; if edited, it shall record an "edited" indicator and timestamp.
- FR-COM-4: Comments shall be visible to all parties on the ticket (creator, assigned engineer, Administrators); a future-facing "internal note" visibility option is noted in Section 17.

### 7.7 Attachments
- FR-ATT-1: The system shall allow file uploads on ticket creation and on comments.
- FR-ATT-2: Supported file types shall include images (JPG, PNG), PDF, Word (DOC/DOCX), Excel (XLS/XLSX), and ZIP archives.
- FR-ATT-3: The system shall enforce a maximum file size per upload and a maximum number of attachments per ticket/comment (specific limits to be finalized in design phase).
- FR-ATT-4: Every attachment shall be associated with exactly one ticket (and, where applicable, one comment) and shall be retrievable only by users authorized to view that ticket.

### 7.8 Search
- FR-SRCH-1: The system shall support search by ticket number, title, category, priority, status, creating user, assigned engineer, and date/date range.
- FR-SRCH-2: Search results shall respect role-based visibility (a User only ever sees their own tickets in results; a Support Engineer sees assigned tickets; an Administrator sees all).

### 7.9 Filtering
- FR-FILT-1: List views shall support filtering by status, priority, category, assigned engineer, and date range, usable in combination.

### 7.10 Sorting & Pagination
- FR-SORT-1: List views shall support sorting by creation date, priority, status, and last-updated date, ascending or descending.
- FR-PAGE-1: All list views returning more than a configurable threshold of results shall be paginated.

### 7.11 Notification System
- FR-NOTIF-1: The system shall generate a notification on: ticket created, ticket assigned, status updated, priority changed, comment added, ticket resolved, ticket closed.
- FR-NOTIF-2: Notifications shall appear on the relevant dashboard and in a persistent Notification Center, and shall be marked read/unread.
- FR-NOTIF-3: The notification system shall be built behind an abstraction that allows adding email delivery in a future phase without changing notification-triggering logic (see Section 15).

### 7.12 Reporting
- FR-REP-1: An Administrator shall be able to generate reports on: tickets per category, average resolution time, engineer performance (tickets closed, average time), most common issue types, and monthly/weekly statistics.
- FR-REP-2: Reports shall be viewable on-screen (with charts) and exportable.

### 7.13 Profile Management
- FR-PROF-1: Every authenticated user shall be able to view and update their profile information, change their password, and upload an avatar.

---

## 8. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Reliability** | The system shall behave predictably under normal and edge-case input; failures shall degrade gracefully with clear user-facing messaging, never silent data loss. |
| **Scalability** | The data model and list views shall be designed to remain performant as ticket volume grows into the tens of thousands without redesign (pagination, indexed search fields). |
| **Security** | Passwords shall be hashed (never stored in plaintext); all authenticated routes shall enforce role-based access control; file uploads shall be validated by type/size and never executed; session tokens shall expire. |
| **Responsiveness (Performance)** | Common list and dashboard views shall load within an acceptable interactive threshold under normal data volumes; the UI shall never block on large result sets (pagination is mandatory, not optional). |
| **Accessibility** | The UI shall support full keyboard navigation, correct ARIA labeling, WCAG-AA-level color contrast, and screen-reader-compatible form semantics. |
| **Responsive Layout** | The UI shall render correctly and remain fully usable on desktop, laptop, tablet, and mobile viewport widths. |
| **Maintainability** | The codebase shall be modular, with clear separation between roles/modules, so features can be modified or extended without cross-cutting rewrites. |
| **Extensibility** | The design shall not preclude the future integrations listed in Section 15 (OAuth/SSO, queues, containerization, mobile clients, microservices). |
| **Auditability** | Every ticket's activity timeline shall be immutable and complete — status changes, assignments, and comments are append-only history, not overwritten state. |
| **Usability** | Every screen shall make the current state, available actions, and next step unambiguous to the user (see Section "Design Principles" below). |

### Error Handling Principles
- Errors surfaced to users shall always be actionable and in plain language (e.g., "That file is too large — please upload something under 10 MB" rather than a raw exception).
- Internal errors (stack traces, system internals) shall never be exposed to the end user; they shall be logged server-side for engineering triage.

---

## 9. Feature Breakdown

| Module | Core Features |
|---|---|
| Authentication | Register, Login, Logout, Email Verification, Forgot/Reset Password, Change Password |
| Dashboards | Role-specific dashboard for User, Support Engineer, Administrator |
| Ticketing | Create, view, update, close, reopen, assign, soft-delete (admin), search, filter, sort, paginate, export |
| Comments | Threaded discussion per ticket, with attachments and edit tracking |
| Attachments | Upload/retrieve files scoped to a ticket or comment, type/size validated |
| Notifications | Event-driven in-app notifications with a Notification Center |
| Reporting & Analytics | Category/engineer/time-based reports, exportable, chart-based dashboards |
| Search | Multi-field structured + text search, respecting role visibility |
| Profile Management | Edit profile, change password, upload avatar |
| User & Role Administration | Admin management of users, roles, categories, priorities, engineer assignment |

---

## 10. Ticket Lifecycle

### Status Definitions

| Status | Meaning |
|---|---|
| **OPEN** | Ticket created, not yet assigned to an engineer |
| **ASSIGNED** | An engineer has been assigned but has not yet begun work |
| **IN_PROGRESS** | The assigned engineer is actively working the issue |
| **WAITING_FOR_USER** | The engineer needs more information/action from the requester before proceeding |
| **RESOLVED** | The engineer has completed the fix; awaiting requester confirmation |
| **CLOSED** | The requester (or an Administrator, after a timeout) has confirmed the issue is resolved |
| **REOPENED** | A previously Closed ticket has been reactivated because the issue recurred |

### Primary Flow

```
OPEN → ASSIGNED → IN_PROGRESS → WAITING_FOR_USER → RESOLVED → CLOSED
```

### Lifecycle Rules
- `WAITING_FOR_USER` always returns to `IN_PROGRESS` once the user responds — it is not a dead end.
- `RESOLVED` can bounce back to `IN_PROGRESS` if the requester disputes the resolution instead of closing it.
- `CLOSED` can transition to `REOPENED` within the reopen window (Section 7, FR-TICK-7); `REOPENED` re-enters the flow at `ASSIGNED` (typically to the same engineer) rather than `OPEN`, preserving continuity.
- Every transition is timestamped and attributed to an actor (user or engineer/admin) in the ticket's activity timeline (FR-TICK-12).
- Skipped transitions (e.g., Open directly to Resolved with no engineer ever assigned) are disallowed by FR-FLOW-1, since accountability depends on an assignment existing before resolution.

---

## 11. Use Case Diagrams (Textual)

### UC-1: Create Ticket
- **Actor**: User
- **Preconditions**: User is authenticated and verified.
- **Main Flow**: User navigates to "New Ticket" → enters title, description, category, priority → optionally attaches files → submits.
- **Postconditions**: Ticket created in OPEN status with a unique ticket number; activity timeline initialized; notification generated for relevant Administrators (for assignment).
- **Alternate Flow**: Required field missing → inline validation error shown, ticket not created.

### UC-2: Assign Ticket
- **Actor**: Administrator (or Support Engineer, for self-assignment where enabled)
- **Preconditions**: Ticket exists in OPEN status; at least one active Support Engineer exists.
- **Main Flow**: Administrator opens ticket → selects an engineer from active engineer list → confirms.
- **Postconditions**: Ticket status moves to ASSIGNED; engineer notified; activity timeline updated.

### UC-3: Resolve Ticket
- **Actor**: Support Engineer
- **Preconditions**: Ticket is ASSIGNED or IN_PROGRESS and owned by this engineer.
- **Main Flow**: Engineer updates status to IN_PROGRESS → works issue, optionally requests info (→ WAITING_FOR_USER) → marks RESOLVED with a resolution comment.
- **Postconditions**: Requester notified; ticket enters RESOLVED awaiting confirmation.

### UC-4: Close / Reopen Ticket
- **Actor**: User
- **Preconditions**: Ticket is RESOLVED (to close) or CLOSED within the reopen window (to reopen).
- **Main Flow (Close)**: User reviews resolution → confirms → ticket moves to CLOSED.
- **Main Flow (Reopen)**: User indicates issue recurred → ticket moves to REOPENED → re-enters ASSIGNED with prior engineer, history intact.

### UC-5: Escalate Ticket
- **Actor**: Support Engineer
- **Preconditions**: Ticket is assigned to the engineer and requires expertise/priority beyond their scope.
- **Main Flow**: Engineer flags ticket for escalation with a reason → Administrator notified → Administrator reassigns or elevates priority.

### UC-6: Generate Report
- **Actor**: Administrator
- **Preconditions**: Sufficient ticket data exists for the selected period.
- **Main Flow**: Administrator selects report type and date range → system aggregates data → displays chart/table → Administrator exports if needed.

### UC-7: Comment on Ticket
- **Actor**: User or Support Engineer (or Administrator)
- **Preconditions**: Actor has visibility into the ticket (creator, assignee, or admin).
- **Main Flow**: Actor opens ticket → adds comment text and optional attachment → submits.
- **Postconditions**: Comment appended to thread; other ticket participants notified.

---

## 12. System Scope

### In Scope
- Web application usable on desktop and mobile browsers (responsive, not a native mobile app).
- Three roles: User, Support Engineer, Administrator, with role-based dashboards and permissions.
- Full ticket lifecycle management, threaded comments, attachments.
- In-app notification system (Notification Center + dashboard surfacing).
- Search, filter, sort, pagination, and export on ticket lists.
- Administrator-facing reporting/analytics with charts.
- Profile management for all roles.

### Out of Scope (this phase)
- Native mobile applications (iOS/Android) — noted as future roadmap (Section 15).
- Real outbound email delivery — the notification system will be built behind an abstraction ready for it, but actual SMTP/email-provider integration is not part of this phase.
- Third-party SSO (Google/Microsoft login) — architecture should not block it, but it is not implemented now.
- Multi-tenant support (multiple separate organizations in one deployment) — this phase assumes a single organization.
- SLA-based automatic breach alerts / auto-escalation timers — noted as a suggested improvement (Section 17).
- Public customer-facing portal separate from internal employee use — this phase treats all requesters as internal Users.

---

## 13. Assumptions

- A1: Only Users self-register; Support Engineer and Administrator accounts are created/promoted by an existing Administrator. (Prevents privilege escalation via open registration — a standard enterprise control.)
- A2: A ticket has exactly one assigned Support Engineer at a time (no multi-engineer co-assignment in this phase).
- A3: Category and priority, once set at ticket creation, may be adjusted by a Support Engineer or Administrator during triage, but not arbitrarily by the requester after assignment — this preserves reporting integrity.
- A4: "Delete" for Administrators is a soft delete (hidden, retained for audit) rather than permanent erasure, to preserve historical reporting integrity; a true hard-delete/purge capability, if ever needed, is a separate, more tightly controlled operation outside this phase's scope.
- A5: The reopen window for a Closed ticket is a configurable business rule (a default such as 7 days is assumed) rather than unlimited or absent.
- A6: Single organization/single deployment — no multi-tenancy assumed.
- A7: Notification delivery in this phase is in-app only; the notification system's design should not preclude adding email as a delivery channel later.
- A8: File storage location/provider is a design-phase decision, not fixed here; this document only fixes the functional requirement that attachments are supported with specific file types and are scoped to a ticket.

---

## 14. Constraints

- C1: No code, schema, or API design shall be produced until this SRS is reviewed and approved.
- C2: The system must remain buildable as a single-team/portfolio-scale project — features are scoped for depth of engineering quality over breadth of surface area.
- C3: The design must not preclude the future integrations listed in Section 15; this constrains architecture choices in the (later) design phase, not this document's content.
- C4: All error messages shown to end users must avoid technical/internal detail (Section 8).
- C5: Accessibility and responsive-layout requirements (Section 8) apply to every user-facing screen, not a subset.

---

## 15. Future Scope

The following are explicitly deferred but must not be architecturally blocked by decisions made in the design phase:

- OAuth / Google Login / Microsoft Login for authentication.
- JWT-based stateless session/auth tokens (if moving toward a decoupled API + SPA/mobile architecture).
- Redis for caching and/or session storage.
- Kafka / RabbitMQ for asynchronous event processing (e.g., notification dispatch, report generation).
- Docker containerization and Kubernetes orchestration for deployment.
- Cloud deployment on AWS or Azure.
- Decomposition into microservices as scale demands.
- A formal REST API surface for third-party or mobile consumption.
- A native mobile application.

---

## 16. Acceptance Criteria

The requirements phase and subsequent build are considered successful when the following are demonstrably true:

1. **Role separation**: A User cannot access Support Engineer or Administrator dashboards or actions, and vice versa; this is enforced, not just hidden in the UI.
2. **Full lifecycle traceability**: Any ticket can be opened and its complete history — every status change, assignment, and comment, with actor and timestamp — is visible and consistent with Section 10's lifecycle rules.
3. **No orphaned tickets**: Every ticket that reaches RESOLVED has a recorded assigned engineer; the workflow engine rejects any transition that skips required prior states (FR-FLOW-1).
4. **Notifications fire correctly**: Each of the seven triggering events in FR-NOTIF-1 produces a visible notification to the correct recipient(s), verifiable by manual test for each event type.
5. **Search/filter/sort/pagination correctness**: A list of tickets can be narrowed by any single filter or combination from Section 7.9, sorted by any field in Section 7.10, and paginated without missing or duplicating records across pages.
6. **Attachment integrity**: An uploaded file is retrievable only by users authorized to view its parent ticket, rejected if its type/size is out of policy, and correctly associated with the right ticket/comment.
7. **Reporting accuracy**: Each report type in FR-REP-1 produces numbers that reconcile against a manual count of the underlying ticket data for a test dataset.
8. **Accessibility pass**: Key flows (register, login, create ticket, comment, resolve) are fully operable via keyboard alone and pass an automated accessibility audit (e.g., no critical axe-core violations).
9. **Responsive pass**: Key flows render usably at common desktop, tablet, and mobile breakpoints without horizontal scrolling or clipped controls.
10. **Error handling**: Deliberately triggered error conditions (invalid input, unauthorized access, oversized upload) produce plain-language messages with no stack traces or internal identifiers exposed.
11. **No skipped or silent scope changes**: Every feature listed as "In Scope" (Section 12) is present and testable; every item marked "Out of Scope" is absent, not partially implemented in a way that creates false expectations.

---

## 17. Suggested Improvements Beyond the Current Requirements

These are recommendations from the planning phase, not committed scope — offered because they are common gaps in first-generation helpdesk systems and are worth a deliberate scope decision rather than an oversight.

1. **SLA management & auto-escalation**: Define target resolution times per priority level, and auto-escalate or alert when a ticket approaches/breaches its SLA — currently the spec only supports manual engineer-initiated escalation (UC-5).
2. **Internal (private) comments**: Allow Support Engineers/Administrators to leave notes visible only to staff, separate from the user-facing comment thread — useful for handoff context that shouldn't reach the requester.
3. **Knowledge base / self-service deflection**: A searchable article base so common issues (e.g., password reset steps) can be resolved without filing a ticket at all — directly attacks the "duplicate complaints" problem named in Section 3.
4. **Satisfaction (CSAT) rating on close**: A 1–5 rating and optional comment when a User closes a ticket, feeding into engineer performance reporting.
5. **Merge/link duplicate tickets**: Explicit tooling to detect and merge duplicate tickets rather than relying solely on search-before-create discipline.
6. **Audit log for administrative actions**: A distinct, admin-only log of user/role/category management actions (not just ticket activity), for governance and compliance readiness.
7. **Bulk actions**: Bulk-assign, bulk-close, or bulk-tag tickets from list views, valuable once ticket volume grows.
8. **Configurable SLA/priority matrix per category**: Different categories (e.g., Security vs. Others) may warrant different default priority handling — worth a deliberate rule rather than treating all categories uniformly.
9. **Two-factor authentication**: Given Administrator accounts control user/role management, 2FA for elevated roles is a reasonable security hardening ahead of the OAuth/SSO future-roadmap item.
10. **Data retention policy**: A defined policy for how long closed/soft-deleted tickets and their attachments are retained before archival or purge, decided deliberately rather than left implicit.

---

*End of Software Requirements Specification — Version 1.0. This document is the planning-phase deliverable and precedes all architecture, schema, API, and implementation work.*
