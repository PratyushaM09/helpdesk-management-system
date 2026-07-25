package com.helpdesk.attachment.service;

import com.helpdesk.attachment.dto.response.AttachmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Attachment metadata management (FR-ATT-1-4). No file storage
 * implementation - {@code storageKey} is assumed already produced by a
 * future {@code FileStorageService} before any method here is called; this
 * service only ever persists/reads/deletes the metadata row.
 */
public interface AttachmentService {

    /**
     * Records metadata for an already-stored file, scoped to a ticket and,
     * optionally, one of its comments. Exactly one of {@code ticketId}/
     * {@code commentId} may be {@code null}, never both - the comment-scoped
     * upload route has no ticket id of its own to pass, so when
     * {@code ticketId} is {@code null} it is derived from
     * {@code commentId}'s parent ticket instead (Controller Milestone 2).
     *
     * @param ticketId  {@code null} when {@code commentId} is given (derived from the comment's parent ticket)
     * @param commentId {@code null} for a ticket-level attachment; non-null for a comment-level one
     * @throws com.helpdesk.exception.BadRequestException       if both {@code ticketId} and {@code commentId} are {@code null}, if {@code mimeType} isn't allow-listed, or if {@code sizeBytes} exceeds the configured maximum
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket (or {@code commentId}, if given) doesn't resolve, or the ticket isn't visible to the caller
     */
    AttachmentResponse uploadAttachment(Long ticketId, Long commentId, String storageKey,
                                         String originalFilename, String mimeType, long sizeBytes);

    /**
     * Resolves an attachment's metadata for download, re-checking the parent
     * ticket's visibility at request time (03-Security.md §12) - not a
     * redundant check, since the caller of a download may differ from the
     * uploader and ticket assignment may have changed since upload. Returns
     * metadata only; streaming the actual bytes is a future
     * {@code FileStorageService}/Controller concern.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the attachment doesn't exist or its ticket isn't visible to the caller
     */
    AttachmentResponse downloadAttachment(Long attachmentId);

    /**
     * Physically deletes an attachment's metadata (no soft delete). Uploader
     * or ADMIN only.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the attachment doesn't exist
     * @throws com.helpdesk.exception.ForbiddenException        if the caller is neither the uploader nor ADMIN
     */
    void deleteAttachment(Long attachmentId);

    /**
     * Lists a ticket's attachments (both ticket-level and comment-level).
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket doesn't exist or isn't visible to the caller
     */
    Page<AttachmentResponse> listAttachments(Long ticketId, Pageable pageable);
}
