package com.helpdesk.comment.dto.request;

import com.helpdesk.comment.entity.CommentVisibility;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/tickets/{ticketId}/comments} request body (FR-COM-1/2).
 * No {@code author}/{@code ticket} field: the author is the authenticated
 * caller and the ticket is the path variable, never client-supplied.
 * <p>
 * {@code visibility} is nullable, not {@code @NotNull}: a USER-facing
 * client can omit it entirely, and the Service layer defaults a {@code null}
 * value to {@code PUBLIC}. A non-null {@code INTERNAL} value is honored only
 * once the Service layer confirms the caller is staff
 * (SUPPORT_ENGINEER/ADMIN) — that role check, like every other business rule,
 * does not live in this DTO.
 *
 * @param content    entity column {@code content} (unbounded {@code TEXT})
 * @param visibility {@code PUBLIC} or {@code INTERNAL}; {@code null} defaults to {@code PUBLIC}
 */
public record CreateCommentRequest(
        @NotBlank
        String content,

        CommentVisibility visibility
) {
}
