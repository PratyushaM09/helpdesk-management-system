package com.helpdesk.ticket.entity;

import com.helpdesk.common.entity.AuditableEntity;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * The core support-request record (FR-TICK-2). No {@code @OneToMany} fields
 * for comments/attachments/history live here, deliberately — each has its
 * own paginated repository query (05-Database.md §7 Fetch Strategies); an
 * unbounded collection embedded in a ticket-detail fetch would violate the
 * mandatory-pagination rule (FR-PAGE-1) by the back door.
 * <p>
 * {@code @SQLRestriction} makes "exclude soft-deleted rows" the default for
 * every query through {@code TicketRepository} (ADR-0005) — an admin-only
 * "include deleted" path is a deliberate, separate native/JPQL query added
 * when {@code TicketService} exists, never the default.
 * <p>
 * <b>Transition legality is not enforced here.</b> Per 02-Architecture.md §1
 * Principle 4 ("business validation... lives in the Service layer,
 * explicitly"), whether a given status change is legal is a future
 * {@code TicketService}/workflow-validator concern. This entity only
 * guarantees structural invariants (e.g. a non-null {@link #createdBy}), the
 * same division {@code User}/{@code Role} already follow in this codebase.
 */
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_status_priority", columnList = "status, priority"),
        @Index(name = "idx_ticket_assigned_to_status", columnList = "assigned_to, status"),
        @Index(name = "idx_ticket_created_by", columnList = "created_by"),
        @Index(name = "idx_ticket_category_id", columnList = "category_id"),
        @Index(name = "idx_ticket_created_at", columnList = "created_at"),
        @Index(name = "idx_ticket_updated_at", columnList = "updated_at"),
        @Index(name = "idx_ticket_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class Ticket extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Human-readable, globally unique, immutable once generated (FR-TICK-1) — generation strategy is a Service-layer concern, not this entity's. */
    @Column(name = "ticket_number", nullable = false, unique = true, length = 20)
    private String ticketNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    // Categories are seeded/admin-managed, never hard-deleted (FR-PRI-3) - no
    // explicit ON DELETE clause, matching the RESTRICT-by-default pattern
    // User.role already follows.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TicketStatus status = TicketStatus.OPEN;

    // Users are never hard-deleted either (deactivated only) - same RESTRICT
    // reasoning as category above.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** Null until an ADMIN assigns the ticket (it starts {@code OPEN} with no assignee). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Non-null only once an ADMIN soft-deletes this ticket (ADR-0005); paired with {@link #deletedBy}, always set together. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private User deletedBy;

    /** Optimistic-locking version (ADR-0010) - the one entity in this domain with a realistic concurrent-write pattern. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Ticket() {
    }

    public Ticket(String ticketNumber, String title, String description, Category category,
                  TicketPriority priority, User createdBy) {
        this.ticketNumber = ticketNumber;
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public User getDeletedBy() {
        return deletedBy;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Marks this ticket soft-deleted (ADR-0005). Guarded, not reapplied - a
     * second call is not an error, but must not overwrite the original
     * {@link #deletedAt}/{@link #deletedBy}, the same idempotent-state-flip
     * reasoning {@code User.markEmailVerified} already follows.
     */
    public void softDelete(User deletedBy, Instant now) {
        if (this.deletedAt == null) {
            this.deletedAt = now;
            this.deletedBy = deletedBy;
        }
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ticket other)) {
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
        return "Ticket{id=%d, ticketNumber=%s, status=%s}".formatted(id, ticketNumber, status);
    }
}
