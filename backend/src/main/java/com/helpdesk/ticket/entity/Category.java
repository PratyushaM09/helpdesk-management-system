package com.helpdesk.ticket.entity;

import com.helpdesk.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Configurable ticket taxonomy (FR-PRI-2/3) — a table, not an enum like
 * {@link TicketStatus}/{@code Priority}, specifically because it is
 * administered at runtime (add/rename/deactivate) rather than fixed by the
 * specification (05-Database.md §8).
 * <p>
 * Deactivated, never deleted: a category still referenced by historical
 * tickets must remain joinable (FR-PRI-3, ADR-0005's soft-delete philosophy
 * applied here as a boolean flag rather than a timestamp, since a category
 * has no meaningful "when" to preserve, unlike a deleted Ticket).
 */
@Entity
@Table(name = "categories")
public class Category extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public Category(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    /** Excludes this category from new-ticket pickers while leaving historical tickets that reference it intact (FR-PRI-3). */
    public void deactivate() {
        this.active = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Category other)) {
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
        return "Category{id=%d, name=%s, active=%s}".formatted(id, name, active);
    }
}
