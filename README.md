# HelpDesk Management System

A centralized web platform for reporting, assigning, tracking, and resolving internal support tickets, with dedicated experiences for Users, Support Engineers, and Administrators.

**Live:** [Frontend](https://helpdesk-management-system-1.onrender.com) · [Backend API](https://helpdesk-management-system-n5tt.onrender.com/api/v1/health) — hosted on Render's free tier, so the backend spins down after ~15 minutes idle; the first request after that can take 30-60s to wake it back up.

## Project Status

**Phases 1–4 implemented; Phase 5 (production readiness) in progress.** The
SRS ([docs/01-SRS.md](docs/01-SRS.md)), Software Architecture Document set,
and full Enterprise Security Architecture Document (SecAD) are accepted.
[backend/](backend/) (Spring Boot REST API — auth, tickets, comments,
attachments, users, roles) and [frontend/](frontend/) (static HTML/CSS/JS)
are both fully implemented, integrated, and **deployed live** (Render: a
static site for the frontend, a web service for the backend, MySQL via
Aiven). Phase 5 has completed Dockerization (Milestone 1 — see
[DOCKER.md](DOCKER.md)) and a production configuration review (Milestone 2);
actual deployment happened directly on Render rather than through the Docker
images, since local Docker execution verification is currently blocked by a
Docker Desktop version issue on the development machine — the
`docker-compose.yml`/`Dockerfile`s exist and are believed correct, but
haven't been run end-to-end locally to confirm.

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
├── backend/                          Spring Boot REST API (Java 21, Maven, MySQL)
├── frontend/                         Static HTML/CSS/JS frontend (no build step)
├── docker-compose.yml, DOCKER.md,
│   .env.example                      Local containerized startup (Phase 5, Milestone 1)
└── README.md
```

## Reading Order

1. [docs/01-SRS.md](docs/01-SRS.md) — what the system must do and why.
2. [docs/02-Architecture.md](docs/02-Architecture.md) — how it's built: modules, layering, package structure, request/notification/upload flows, design patterns, scalability plan.
3. [docs/03-Security.md](docs/03-Security.md), [docs/04-API-Design.md](docs/04-API-Design.md), [docs/05-Database.md](docs/05-Database.md), [docs/06-Testing.md](docs/06-Testing.md) — detailed design following architecture sign-off.
4. [docs/07-Security-Architecture.md](docs/07-Security-Architecture.md) → [08-Security-Controls.md](docs/08-Security-Controls.md) → [09-Security-Operations.md](docs/09-Security-Operations.md) → [10-Security-Assurance.md](docs/10-Security-Assurance.md) — the full Enterprise Security Architecture Document (SecAD): threat model, authentication/authorization/session design, Spring Security design, OWASP Top 10 compliance mapping, and the future security roadmap.
5. [docs/ADR/](docs/ADR/) and [docs/SDR/](docs/SDR/) — the specific hard-to-reverse architecture and security decisions made along the way, indexed in [docs/Decisions.md](docs/Decisions.md) and [docs/Security-Decisions.md](docs/Security-Decisions.md) respectively.
6. [docs/11-Development-Rules.md](docs/11-Development-Rules.md) — the binding coding standard for all implementation. **Note:** implementation followed this document's MySQL/JavaScript stack (not the PostgreSQL/React SPA stack originally chosen in [docs/02-Architecture.md](docs/02-Architecture.md)/ADR-0002) — Thymeleaf was pulled in as a dependency early on but was never actually used (this is a pure REST API; see backend/pom.xml) and was removed in Phase 5, Milestone 2. Tailwind was likewise not used — the frontend is hand-written CSS. This divergence from ADR-0002 was never formally re-recorded as its own ADR; worth doing if the document set is revisited.