package com.helpdesk.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Backs concurrency-safe {@code ticketNumber} generation (FR-TICK-1, format
 * {@code HD-{year}-{sequence}}) — one row per calendar year, {@code year}
 * itself as the natural primary key. Deliberately not
 * {@code AuditableEntity}: this is a pure infrastructure counter, not a
 * business record with a meaningful creation/modification history.
 * <p>
 * Concurrency safety comes from the caller ({@code TicketServiceImpl})
 * reading this row through {@code TicketSequenceRepository.findByYear}'s
 * {@code PESSIMISTIC_WRITE} lock, held for the rest of that transaction —
 * not from anything in this class. {@link #incrementAndGet()} itself is a
 * plain, unsynchronized field mutation; it is safe only because at most one
 * transaction ever holds the row lock at a time.
 */
@Entity
@Table(name = "ticket_sequences")
public class TicketSequence {

    // Column deliberately not named "year" - it is a SQL reserved word
    // (rejected outright by H2's DDL parser; fragile even where an engine
    // happens to tolerate it unquoted). The Java-level property/getter/
    // constructor/repository-query-derivation all stay "year" - only the
    // database column name differs, so nothing else needed to change.
    @Id
    @Column(name = "seq_year")
    private int year;

    @Column(name = "next_value", nullable = false)
    private long nextValue = 0;

    protected TicketSequence() {
    }

    public TicketSequence(int year) {
        this.year = year;
    }

    public int getYear() {
        return year;
    }

    public long getNextValue() {
        return nextValue;
    }

    /** Bumps and returns the next sequence value for this year (first call returns 1). */
    public long incrementAndGet() {
        this.nextValue += 1;
        return this.nextValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TicketSequence other)) {
            return false;
        }
        return year == other.year;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "TicketSequence{year=%d, nextValue=%d}".formatted(year, nextValue);
    }
}
