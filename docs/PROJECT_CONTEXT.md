# HelpDesk Management System

## Project Status

Current Phase:
Planning Completed

Next Phase:
Backend Development

---

## Completed Documents

- 01-SRS.md
- 02-Architecture.md
- 03-Security.md (summary — see 07–10 for the full Security Architecture Document)
- 04-API-Design.md
- 05-Database.md
- 06-Testing.md
- 07-Security-Architecture.md through 10-Security-Assurance.md (full SecAD)
- 11-Development-Rules.md (engineering handbook / coding standard)
- ADR/ (0001–0012), Decisions.md
- SDR/ (0001–0017), Security-Decisions.md

**Open item:** 11-Development-Rules.md was written against MySQL + Thymeleaf + Tailwind + JavaScript (as specified for that task), which conflicts with the PostgreSQL + React SPA stack decided in 02-Architecture.md / ADR-0002. Needs a reconciling decision before backend development starts.

---

## Technology Stack

Backend
- Java 21
- Spring Boot 3
- Spring Security
- Spring Data JPA
- Hibernate
- MySQL

Frontend
- Thymeleaf
- Tailwind CSS
- JavaScript

---

## Architecture

- Modular Monolith
- Layered Architecture
- Clean Architecture
- SOLID
- DTO Pattern
- Repository Pattern

---

## Roles

- USER
- SUPPORT
- ADMIN

---

## Rules

Never expose entities.

Always use DTOs.

Use constructor injection only.

Repository → Service → Controller.

Global Exception Handling.

Bean Validation.

MapStruct.

Auditing.

Optimistic Locking.

Never modify finalized documents without permission.

Always keep the project runnable.