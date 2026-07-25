package com.helpdesk.tickethistory.entity;

import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Immutable timeline row for a single ticket change (FR-TICK-12, ADR-0006).
 * Append-only by omission, not just convention: no setters exist here, and
 * {@code TicketHistoryRepository} deliberately exposes no update/delete
 * method - the only way to affect this table is {@code save} a new row.
 * <p>
 * No {@code updated_at}: same reasoning as {@code Attachment} - an
 * append-only entity's missing update timestamp is itself proof no update
 * path exists, not an oversight.
 * <p>
 * {@code internal} mirrors the visibility of the {@code Comment} a
 * {@code COMMENT_ADDED} row refers to (never the comment content itself,
 * which stays the single source of truth on {@code Comment}) - a USER's
 * timeline view must not reveal that an internal note was added, the same
 * anti-leak principle the comment's own visibility already enforces.
 */
@Entity
@Table(name = "ticket_history", indexes = {
        @Index(name = "idx_ticket_history_ticket_id_created_at", columnList = "ticket_id, created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class TicketHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private TicketHistoryAction action;

    @Column(name = "old_value", length = 255)
    private String oldValue;

    @Column(name = "new_value", length = 255)
    private String newValue;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "internal", nullable = false)
    private boolean internal = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketHistory() {
    }

    public TicketHistory(Ticket ticket, User actor, TicketHistoryAction action,
                          String oldValue, String newValue, String note, boolean internal) {
        this.ticket = ticket;
        this.actor = actor;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.note = note;
        this.internal = internal;
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getActor() {
        return actor;
    }

    public TicketHistoryAction getAction() {
        return action;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getNote() {
        return note;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TicketHistory other)) {
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
        return "TicketHistory{id=%d, ticketId=%s, action=%s}".formatted(id, ticket != null ? ticket.getId() : null, action);
    }
}
