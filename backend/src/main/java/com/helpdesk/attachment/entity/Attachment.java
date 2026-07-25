package com.helpdesk.attachment.entity;

import com.helpdesk.comment.entity.Comment;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * File metadata only (Phase 3 design review, Final Adjustment 4) - no cloud
 * storage, S3, or virus-scanning implementation yet; {@code storageKey} is an
 * opaque handle a future {@code FileStorageService} resolves.
 * <p>
 * Deliberately not an {@link com.helpdesk.common.entity.AuditableEntity}:
 * an attachment is never updated in place (only deleted by its uploader or
 * an admin), so it carries {@code created_at} only - the same "absence of
 * updated_at is itself a structural guarantee" reasoning append-only
 * entities in this domain already follow. No setters exist for the same
 * reason: every field is fixed at upload time.
 * <p>
 * {@code ticket} is always set, even for a comment-scoped upload (denormalized
 * from {@link #comment}'s parent ticket) - keeps "which ticket does this
 * attachment belong to" a single-hop query for the ticket-visibility
 * authorization check, rather than a join through {@code Comment}
 * (05-Database.md §3).
 */
@Entity
@Table(name = "attachments", indexes = {
        @Index(name = "idx_attachment_ticket_id", columnList = "ticket_id"),
        @Index(name = "idx_attachment_comment_id", columnList = "comment_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** Null for a ticket-level attachment; set for a comment-level one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Attachment() {
    }

    public Attachment(Ticket ticket, Comment comment, String storageKey, String originalFilename,
                       String mimeType, long sizeBytes, User uploadedBy) {
        this.ticket = ticket;
        this.comment = comment;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public Comment getComment() {
        return comment;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Attachment other)) {
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
        return "Attachment{id=%d, storageKey=%s, originalFilename=%s}".formatted(id, storageKey, originalFilename);
    }
}
