package com.helpdesk.comment.mapper;

import com.helpdesk.comment.dto.request.CreateCommentRequest;
import com.helpdesk.comment.dto.request.UpdateCommentRequest;
import com.helpdesk.comment.dto.response.CommentResponse;
import com.helpdesk.comment.entity.Comment;
import com.helpdesk.comment.entity.CommentVisibility;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.user.entity.User;

import java.time.Instant;

/**
 * Structural DTO ⇄ Entity conversion only for {@link Comment} - no business
 * rule, no repository access. {@code ticket}/{@code author} are received
 * already-resolved (the ticket is the path variable, the author is the
 * authenticated caller); {@code resolvedVisibility} is also received
 * already-resolved - whether a {@code null} {@code request.visibility()}
 * defaults to {@code PUBLIC}, and whether an {@code INTERNAL} request is
 * permitted for this caller, are both Service-layer decisions
 * ({@code CreateCommentRequest}'s own Javadoc), never this mapper's.
 * <p>
 * {@code updateEntity} takes an explicit {@code Instant now} rather than
 * calling {@code Instant.now()} itself - a mapper deciding "what time is it"
 * would be exactly the kind of hidden business-adjacent behavior
 * {@code AccountMapper}'s Javadoc explains why it avoids embedding in a
 * mapper; the Service supplies the instant, this method only forwards it
 * into {@link Comment#edit(String, Instant)}.
 */
public interface CommentMapper {

    /** Builds a new, transient {@link Comment} from a create request. */
    Comment toEntity(CreateCommentRequest request, Ticket ticket, User author, CommentVisibility resolvedVisibility);

    /** Applies an edit onto an already-managed {@link Comment} in place, via its own {@code edit} domain method. */
    void updateEntity(Comment comment, UpdateCommentRequest request, Instant now);

    /** Projects a {@link Comment} to its API-safe response shape. */
    CommentResponse toResponse(Comment comment);
}
