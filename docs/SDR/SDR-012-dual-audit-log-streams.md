# SDR-012: Dual Audit Log Streams — Ticket Activity vs. Administrative/Security

**Status:** Accepted
**Date:** 2026-07-19
**Extends:** ADR-0006 (Append-Only Ticket Activity Timeline)
**Related:** [09-Security-Operations.md §15](../09-Security-Operations.md#15-audit-strategy)

## Decision
Maintain two separate, both append-only, audit log tables: `TicketActivity` (visible to ticket participants — creator, assignee, Administrators) and `AdminAuditLog` (Administrator/compliance-only visibility, covering authentication events, credential changes, role/permission changes, security violations, and administrative ticket/category actions).

## Reason
ADR-0006 established the append-only ticket timeline for FR-TICK-12 (workflow auditability). This SecAD phase requires a materially broader audit surface — authentication events, authorization failures, CSRF/rate-limit violations, and role changes are security-relevant facts that no ticket participant should see (a `USER` has no legitimate reason to see another user's login-failure history, and a login failure isn't associated with any ticket at all in the first place), but that an Administrator/compliance reviewer absolutely needs visibility into (SRS §17.6). Merging these into one stream would either over-expose security-sensitive detail to ticket participants or under-serve the administrative audit need by forcing security events into a ticket-shaped schema they don't naturally fit.

## Alternatives Considered
- **Single unified audit table for everything:** rejected — the visibility/access-control requirements of the two audiences (ticket participants vs. Administrators-only) are fundamentally different, and forcing both into one table would require per-row visibility logic that's easy to get wrong (a single missed filter condition could expose security-sensitive rows to a `USER` querying "their" ticket's activity) — two physically separate tables make the access-control boundary structural, not logic-dependent.
- **No dedicated administrative audit log — rely on general application logs (Section 16) alone:** rejected — general logs (Section 16 of [09-Security-Operations.md](../09-Security-Operations.md#16-logging-strategy)) are optimized for operational troubleshooting (rotated, potentially shorter-retained, not natively queryable by "show me every action Administrator X took last month") — a distinct, structured, longer-retained database table is what actually satisfies SRS §17.6's governance/compliance framing and supports the retention policy in Section 15.3 of [09-Security-Operations.md](../09-Security-Operations.md#153-audit-retention-strategy).

## Pros
- Access-control boundary between the two audiences is structural (two tables, two repository-level visibility rules), not a runtime filter that could be forgotten on a new query path.
- Each stream's schema fits its actual data shape (`TicketActivity` is inherently ticket-scoped; `AdminAuditLog` uses a lightweight polymorphic target reference since it spans users, categories, and security events that aren't all ticket-related).
- Both streams share the append-only design discipline established by ADR-0006 (no `UPDATE`/`DELETE` repository methods exist for either) — one architectural pattern, two applications of it.

## Cons
- Two tables to query/join if a future need arises to correlate "what was happening on this ticket" with "what security events occurred around the same time" — an accepted tradeoff; such correlation is expected to be rare (primarily incident-investigation-driven) and can be done via `traceId`/timestamp correlation (Section 16.1 of [09-Security-Operations.md](../09-Security-Operations.md#161-what-is-logged-security-relevant)) rather than requiring a single denormalized table.

## Future Impact
If a future SIEM/log-aggregation pipeline is introduced ([10-Security-Assurance.md §21](../10-Security-Assurance.md#21-future-security-roadmap)), the `AdminAuditLog` stream is the natural, already-structured feed for it — its schema and event-category taxonomy (Section 15.2 of [09-Security-Operations.md](../09-Security-Operations.md#152-administrative--security-audit-log-adminauditlog)) map directly onto typical SIEM alerting rules (repeated authorization failures, lockout patterns) without redesign.
