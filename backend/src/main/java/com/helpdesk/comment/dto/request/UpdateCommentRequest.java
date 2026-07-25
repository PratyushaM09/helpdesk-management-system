package com.helpdesk.comment.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code PUT /api/v1/comments/{id}} request body (FR-COM-3) — the comment
 * author's own edit. No {@code visibility}: changing a comment's visibility
 * after the fact is not a supported edit in this phase. No {@code version}:
 * {@code Comment} carries no {@code @Version} column (ADR-0010 scopes
 * optimistic locking to {@code Ticket} only, reasoning that a comment is
 * only ever edited by its single author) — accepting a version value this
 * DTO can never actually check would imply a concurrency guarantee that
 * doesn't exist. Revisit if {@code Comment} ever gains its own version
 * column.
 *
 * @param content entity column {@code content}
 */
public record UpdateCommentRequest(
        @NotBlank
        String content
) {
}
