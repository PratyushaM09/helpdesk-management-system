package com.helpdesk.comment.dto.response;

import com.helpdesk.comment.entity.CommentVisibility;

import java.time.Instant;

/**
 * Payload for {@code GET /api/v1/tickets/{ticketId}/comments} /
 * {@code POST .../comments}. Deliberately excludes the {@code author}
 * entity/id — only its display name is exposed, the same "never the full
 * associated entity" rule {@code TicketSummaryResponse.assignedToName}
 * already follows.
 *
 * @param id         surrogate identifier
 * @param authorName the comment author's display name
 * @param content    entity column {@code content}
 * @param visibility {@code PUBLIC} or {@code INTERNAL}; filtering out
 *                   {@code INTERNAL} rows a USER may not see is a
 *                   Service-layer concern, not this DTO's
 * @param edited     whether the comment has been edited (FR-COM-3)
 * @param editedAt   when it was most recently edited; {@code null} if never
 * @param createdAt  when the comment was posted
 */
public record CommentResponse(
        Long id,
        String authorName,
        String content,
        CommentVisibility visibility,
        boolean edited,
        Instant editedAt,
        Instant createdAt
) {
}
