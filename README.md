# HelpDesk Management System

A centralized web platform for reporting, assigning, tracking, and resolving internal support tickets, with dedicated experiences for Users, Support Engineers, and Administrators.

## Project Status

**Architecture and Security Architecture phases complete.** The SRS ([docs/01-SRS.md](docs/01-SRS.md)) is approved, the Software Architecture Document set is accepted, and the full Enterprise Security Architecture Document (SecAD) is accepted. No implementation exists yet in [backend/](backend/) or [frontend/](frontend/) — these phases produced design only, no code.

## Repository Structure

```
HelpDesk-Management-System/
│
├── docs/
│   ├── 01-SRS.md                     Software Requirements Specification (start here)
│   ├── 02-Architecture.md            System architecture — modules, packages, flows, patterns, roadmap
│   ├── 03-Security.md                Security design summary (see 07–10 for the full SecAD)
│   ├── 04-API-Design.md              REST API blueprint — full endpoint inventory
│   ├── 05-Database.md                Data model — entities, ER diagram, indexing, integrity rules
│   ├── 06-Testing.md                 Testing architecture — pyramid, strategy, folder structure
│   ├── 07-Security-Architecture.md   SecAD Part I — goals, threat model, authN/authZ, session, password policy, permission matrix
│   ├── 08-Security-Controls.md       SecAD Part II — Spring Security design, validation, file upload, API/application security
│   ├── 09-Security-Operations.md     SecAD Part III — database security, audit, logging, configuration, error handling
│   ├── 10-Security-Assurance.md      SecAD Part IV — security testing, OWASP Top 10 mapping, future roadmap, SDR index
│   ├── 11-Development-Rules.md       Engineering handbook — coding, layering, testing, git, and review standards
│   ├── ADR/                          Architecture Decision Records (0001–0012)
│   ├── SDR/                          Security Decision Records (0001–0017)
│   ├── Decisions.md                  Index of accepted ADRs
│   └── Security-Decisions.md         Index of accepted SDRs
│
├── backend/                          (empty — implementation not started)
├── frontend/                         (empty — implementation not started)
└── README.md
```

## Reading Order

1. [docs/01-SRS.md](docs/01-SRS.md) — what the system must do and why.
2. [docs/02-Architecture.md](docs/02-Architecture.md) — how it's built: modules, layering, package structure, request/notification/upload flows, design patterns, scalability plan.
3. [docs/03-Security.md](docs/03-Security.md), [docs/04-API-Design.md](docs/04-API-Design.md), [docs/05-Database.md](docs/05-Database.md), [docs/06-Testing.md](docs/06-Testing.md) — detailed design following architecture sign-off.
4. [docs/07-Security-Architecture.md](docs/07-Security-Architecture.md) → [08-Security-Controls.md](docs/08-Security-Controls.md) → [09-Security-Operations.md](docs/09-Security-Operations.md) → [10-Security-Assurance.md](docs/10-Security-Assurance.md) — the full Enterprise Security Architecture Document (SecAD): threat model, authentication/authorization/session design, Spring Security design, OWASP Top 10 compliance mapping, and the future security roadmap.
5. [docs/ADR/](docs/ADR/) and [docs/SDR/](docs/SDR/) — the specific hard-to-reverse architecture and security decisions made along the way, indexed in [docs/Decisions.md](docs/Decisions.md) and [docs/Security-Decisions.md](docs/Security-Decisions.md) respectively.
6. [docs/11-Development-Rules.md](docs/11-Development-Rules.md) — the binding coding standard for all implementation from this point forward. **Note:** this document targets MySQL/Thymeleaf/Tailwind/JavaScript as given for that phase, which differs from the PostgreSQL/React SPA stack chosen in [docs/02-Architecture.md](docs/02-Architecture.md)/ADR-0002 — this needs a reconciling decision before implementation begins in earnest.


HelpDesk Management System
|
Enterprise Ticket Management System
|
Status
|
Planning Phase Completed
|
Documentation
|
docs/01-SRS.md
|
docs/02-Architecture.md
|
docs/03-Security.md
|
Next
|
Backend Development