# ADR-0008: File Storage Abstraction for Attachments

**Status:** Accepted
**Date:** 2026-07-19

## Context

Assumption A8 explicitly defers the file storage location/provider to the design phase without fixing it — only requiring that attachments are supported, type/size validated, and scoped to a ticket (FR-ATT-1–4). SRS §15 names AWS cloud deployment as a future direction, which implies object storage (S3) may replace local disk storage without notice to the rest of the system.

## Decision

Define a `FileStorageService` interface (Strategy pattern — see [02-Architecture.md](../02-Architecture.md#design-patterns)) in the Attachments module's service layer, with a single method contract for `store`, `retrieve`, and `delete` operating on a logical `StorageKey`, independent of *where* bytes physically live. Ship one implementation for this phase:

- `LocalDiskStorageService` — stores files under an application-configured root directory, keyed by `{ticketId}/{uuid}-{sanitizedFilename}`, with the physical path never exposed to the client (files are streamed back through an authenticated controller endpoint, not served as static assets — see [03-Security.md](../03-Security.md#file-upload-flow)).

The `Attachment` entity stores only the logical `StorageKey`/metadata (original filename, MIME type, size, uploader, ticket/comment association), never a raw filesystem path leaked to any DTO.

## Consequences

- **Positive:** A future `S3StorageService` implementing the same interface (for the AWS roadmap item, SRS §15) is a configuration/bean-selection change (`@ConditionalOnProperty(storage.provider=s3)`), not a change to the Attachments controller, service business rules, or the `Attachment` entity/schema.
- **Positive:** Because retrieval is always mediated by the controller (never a static file URL), authorization ("retrievable only by users authorized to view that ticket," FR-ATT-4) is enforced identically regardless of backing storage — satisfies Acceptance Criterion 6.
- **Negative:** Slightly more indirection than directly wiring `MultipartFile` to disk; justified because the alternative locks the architecture to local disk, directly contradicting Assumption A8's intent to keep this decision open.
- **Alternatives considered:**
  - *Store files directly in the database (`BYTEA`)* — rejected: bloats the primary datastore, hurts backup/restore times, and doesn't scale toward the AWS/S3 roadmap.
  - *Commit to S3 now* — rejected: adds cloud-account/infra dependency to local development for no requirement that demands it yet (A8 leaves this open deliberately).
