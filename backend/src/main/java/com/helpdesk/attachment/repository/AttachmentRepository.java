package com.helpdesk.attachment.repository;

import com.helpdesk.attachment.entity.Attachment;
import com.helpdesk.comment.entity.CommentVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code findByTicketId} backs a staff/ADMIN caller's full attachment list.
 * {@code findByTicketIdExcludingCommentVisibility} backs a USER caller's
 * view - excludes any attachment whose parent comment has the given
 * visibility (an attachment's own filename/size/timestamp would otherwise
 * leak that an internal discussion exists even though the note's text stays
 * hidden), while still including every ticket-level attachment
 * ({@code comment IS NULL}). Filtered at the query level so pagination
 * totals stay correct.
 */
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Page<Attachment> findByTicketId(Long ticketId, Pageable pageable);

    // LEFT JOIN, not the implicit "a.comment.visibility" path - that path
    // navigation generates an INNER JOIN, which silently drops every
    // comment-less attachment (a.comment IS NULL) from the joined result
    // before the WHERE clause even runs, defeating the "OR a.comment IS
    // NULL" branch entirely. The explicit LEFT JOIN keeps those rows in the
    // result set so "c IS NULL" can actually match them.
    @Query("""
            SELECT a FROM Attachment a
            LEFT JOIN a.comment c
            WHERE a.ticket.id = :ticketId
            AND (c IS NULL OR c.visibility <> :excludedCommentVisibility)
            """)
    Page<Attachment> findByTicketIdExcludingCommentVisibility(@Param("ticketId") Long ticketId,
                                                               @Param("excludedCommentVisibility") CommentVisibility excludedCommentVisibility,
                                                               Pageable pageable);
}
