package com.helpdesk.attachment.dto.response;

import java.time.Instant;

/**
 * Payload for {@code GET /api/v1/tickets/{ticketId}/attachments} and every
 * upload response. Deliberately excludes {@code storageKey} — it is an
 * opaque internal handle a future {@code FileStorageService} resolves, never
 * a client-facing value (exposing it would let a caller guess/enumerate
 * download references, 03-Security.md §12).
 *
 * @param id               surrogate identifier
 * @param originalFilename the filename as uploaded
 * @param mimeType         validated MIME type (FR-ATT-2)
 * @param sizeBytes        file size in bytes
 * @param uploadedByName   the uploader's display name
 * @param createdAt        when the file was uploaded
 */
public record AttachmentResponse(
        Long id,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String uploadedByName,
        Instant createdAt
) {
}
