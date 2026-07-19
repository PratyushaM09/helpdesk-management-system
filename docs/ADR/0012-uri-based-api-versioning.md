# ADR-0012: URI-Based API Versioning

**Status:** Accepted
**Date:** 2026-07-19

## Context

SRS §15 anticipates a formal REST API surface for third-party or mobile consumption, and a decoupled SPA (ADR-0002) is itself an API consumer from day one. Once external consumers exist, breaking contract changes must be introducible without breaking every existing client simultaneously.

## Decision

All REST endpoints are prefixed `/api/v1/...` from the first implementation (not added retroactively). A breaking change to a resource's contract ships as `/api/v2/<resource>` for that resource only (not a wholesale version bump of the entire API), with `v1` maintained until consumers migrate, per a documented deprecation window recorded in [04-API-Design.md](../04-API-Design.md).

## Consequences

- **Positive:** The SPA, and any future mobile client or third-party integrator (SRS §15), get a stable, explicit contract; nothing "silently" changes shape under a live client.
- **Positive:** Versioning per-resource (not globally) avoids the churn of a full API version bump for an unrelated module's change.
- **Negative:** Requires discipline to avoid silently breaking a `v1` contract instead of properly versioning; enforced via API contract tests ([06-Testing.md](../06-Testing.md)) that fail CI on a detected breaking change to a published DTO.
- **Alternatives considered:**
  - *Header-based versioning (`Accept: application/vnd.helpdesk.v1+json`)* — rejected: less discoverable/debuggable than a URI segment, and adds friction to manual testing/exploration (e.g., via Swagger UI) without a proportional benefit at this scale.
  - *No versioning* — rejected: directly blocks the third-party/mobile consumption future-scope item without a rewrite.
