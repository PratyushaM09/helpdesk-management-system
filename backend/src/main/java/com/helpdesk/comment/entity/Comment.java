package com.helpdesk.comment.entity;

import com.helpdesk.common.entity.AuditableEntity;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Threaded discussion on a {@link Ticket} (FR-COM-1). No back-reference
 * collection on {@code Ticket} itself - retrieved only via
 * {@code CommentRepository.findByTicketId} (its own paginated endpoint,
 * 05-Database.md §7), never as part of a ticket-detail fetch.
 */
@Entity
@Table(name = "comments", indexes = {
        @Index(name = "idx_comment_ticket_id", columnList = "ticket_id")
})
public class Comment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** PUBLIC by default; a caller passes INTERNAL only when the Service layer has confirmed the author is staff. */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private CommentVisibility visibility;

    @Column(name = "edited", nullable = false)
    private boolean edited = false;

    @Column(name = "edited_at")
    private Instant editedAt;

    protected Comment() {
    }

    public Comment(Ticket ticket, User author, String content, CommentVisibility visibility) {
        this.ticket = ticket;
        this.author = author;
        this.content = content;
        this.visibility = visibility;
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public CommentVisibility getVisibility() {
        return visibility;
    }

    public boolean isEdited() {
        return edited;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    /** Every call is meaningful (unlike a one-way state flip) - {@link #editedAt} always advances to the latest edit, never guarded. */
    public void edit(String newContent, Instant now) {
        this.content = newContent;
        this.edited = true;
        this.editedAt = now;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Comment other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Comment{id=%d, ticketId=%s, visibility=%s}".formatted(id, ticket != null ? ticket.getId() : null, visibility);
    }
}
