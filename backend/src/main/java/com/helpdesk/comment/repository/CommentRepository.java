package com.helpdesk.comment.repository;

import com.helpdesk.comment.entity.Comment;
import com.helpdesk.comment.entity.CommentVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code findByTicketId} backs a staff/ADMIN caller's full thread view;
 * {@code findByTicketIdAndVisibility} backs a USER caller's PUBLIC-only view
 * - filtered at the query level so pagination totals stay correct, never by
 * discarding rows from an already-paged result.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByTicketId(Long ticketId, Pageable pageable);

    Page<Comment> findByTicketIdAndVisibility(Long ticketId, CommentVisibility visibility, Pageable pageable);
}
