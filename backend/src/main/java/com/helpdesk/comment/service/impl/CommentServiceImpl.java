package com.helpdesk.comment.service.impl;

import com.helpdesk.comment.dto.request.CreateCommentRequest;
import com.helpdesk.comment.dto.request.UpdateCommentRequest;
import com.helpdesk.comment.dto.response.CommentResponse;
import com.helpdesk.comment.entity.Comment;
import com.helpdesk.comment.entity.CommentVisibility;
import com.helpdesk.comment.mapper.CommentMapper;
import com.helpdesk.comment.repository.CommentRepository;
import com.helpdesk.comment.service.CommentService;
import com.helpdesk.exception.ForbiddenException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.security.UserPrincipal;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketService ticketService;
    private final CommentMapper commentMapper;

    public CommentServiceImpl(CommentRepository commentRepository, TicketRepository ticketRepository,
                               UserRepository userRepository, TicketService ticketService, CommentMapper commentMapper) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.ticketService = ticketService;
        this.commentMapper = commentMapper;
    }

    @Override
    @Transactional
    public CommentResponse addComment(Long ticketId, CreateCommentRequest request) {
        // Ticket-visibility is the whole "may this caller touch this ticket
        // at all" rule (USER: own, SUPPORT_ENGINEER: assigned, ADMIN: any) -
        // identical to this milestone's PUBLIC-comment rule, so no separate
        // check is written here.
        ticketService.validateTicketAccess(ticketId);
        Ticket ticket = findTicketOrThrow(ticketId);

        UserPrincipal principal = currentPrincipal();
        CommentVisibility visibility = resolveVisibility(request.visibility(), principal);
        User author = loadAuthenticatedUser();

        Comment comment = commentMapper.toEntity(request, ticket, author, visibility);
        Comment saved = commentRepository.save(comment);

        log.info("Comment added: commentId={}, ticketId={}, visibility={}", saved.getId(), ticketId, visibility);
        return commentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CommentResponse updateComment(Long commentId, UpdateCommentRequest request) {
        Comment comment = findCommentOrThrow(commentId);
        validateCommentAuthor(comment, currentPrincipal());

        commentMapper.updateEntity(comment, request, Instant.now());
        Comment saved = commentRepository.save(comment);

        log.info("Comment updated: commentId={}", commentId);
        return commentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        Comment comment = findCommentOrThrow(commentId);
        validateCommentAuthor(comment, currentPrincipal());

        commentRepository.delete(comment);
        log.info("Comment deleted: commentId={}", commentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> getComments(Long ticketId, Pageable pageable) {
        ticketService.validateTicketAccess(ticketId);

        UserPrincipal principal = currentPrincipal();
        Page<Comment> comments = principal.role() == RoleName.USER
                ? commentRepository.findByTicketIdAndVisibility(ticketId, CommentVisibility.PUBLIC, pageable)
                : commentRepository.findByTicketId(ticketId, pageable);

        return comments.map(commentMapper::toResponse);
    }

    /** {@code null} defaults to PUBLIC; a USER requesting INTERNAL is rejected outright - staff-only, per this milestone's rules. */
    private CommentVisibility resolveVisibility(CommentVisibility requested, UserPrincipal principal) {
        if (requested == null) {
            return CommentVisibility.PUBLIC;
        }
        if (requested == CommentVisibility.INTERNAL && principal.role() == RoleName.USER) {
            throw new ForbiddenException("Customers cannot post internal notes.");
        }
        return requested;
    }

    /**
     * Authorship-only gate, exactly as specified - does not re-check whether
     * the parent ticket is still visible to the caller (e.g. a
     * SUPPORT_ENGINEER reassigned away from a ticket they once commented on
     * can still edit/delete their own comment). A deliberate reading of "only
     * the original author or ADMIN," not an oversight.
     */
    private void validateCommentAuthor(Comment comment, UserPrincipal principal) {
        if (principal.role() == RoleName.ADMIN) {
            return;
        }
        if (comment.getAuthor().getId().equals(principal.userId())) {
            return;
        }
        throw new ForbiddenException("Only the comment's author or an administrator may modify this comment.");
    }

    private Ticket findTicketOrThrow(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", ticketId));
    }

    private Comment findCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));
    }

    private User loadAuthenticatedUser() {
        UserPrincipal principal = currentPrincipal();
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.userId()));
    }

    private UserPrincipal currentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
