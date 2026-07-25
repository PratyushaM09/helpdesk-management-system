package com.helpdesk.comment.service;

import com.helpdesk.comment.dto.request.CreateCommentRequest;
import com.helpdesk.comment.dto.request.UpdateCommentRequest;
import com.helpdesk.comment.dto.response.CommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Comment management (FR-COM-1-4). Ticket-level authorization (does this
 * caller have any business with this ticket at all) is delegated to
 * {@code TicketService.validateTicketAccess} rather than re-derived here -
 * comment-specific rules (PUBLIC/INTERNAL visibility, authorship) are this
 * service's own.
 */
public interface CommentService {

    /**
     * Adds a comment to a ticket the caller can access. {@code null}
     * visibility defaults to {@code PUBLIC}.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket doesn't exist or isn't visible to the caller
     * @throws com.helpdesk.exception.ForbiddenException        if a USER requests {@code INTERNAL} visibility
     */
    CommentResponse addComment(Long ticketId, CreateCommentRequest request);

    /**
     * Edits a comment's content in place. Author or ADMIN only - no
     * optimistic-lock version check ({@code Comment} carries no
     * {@code @Version}, consistent with {@code UpdateCommentRequest}'s own
     * design).
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the comment doesn't exist
     * @throws com.helpdesk.exception.ForbiddenException        if the caller is neither the author nor ADMIN
     */
    CommentResponse updateComment(Long commentId, UpdateCommentRequest request);

    /**
     * Physically deletes a comment (no soft delete for comments). Author or
     * ADMIN only.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the comment doesn't exist
     * @throws com.helpdesk.exception.ForbiddenException        if the caller is neither the author nor ADMIN
     */
    void deleteComment(Long commentId);

    /**
     * Lists a ticket's comments. USER never receives {@code INTERNAL} rows;
     * SUPPORT_ENGINEER/ADMIN receive every visibility - filtered at the
     * query level so pagination totals stay correct.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket doesn't exist or isn't visible to the caller
     */
    Page<CommentResponse> getComments(Long ticketId, Pageable pageable);
}
