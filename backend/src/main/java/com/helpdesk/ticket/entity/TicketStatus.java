package com.helpdesk.ticket.entity;

/**
 * The ticket workflow's fixed states (Phase 3 design review, Final
 * Adjustment 1). Deliberately excludes a {@code REOPENED} value: reopening a
 * {@code CLOSED} ticket is an <b>event</b>, recorded as a {@code TicketHistory}
 * row, not a resting state — the ticket's persisted status goes straight to
 * {@link #ASSIGNED} (same engineer) in that same operation. Transition
 * legality is enforced by the Service layer (a future workflow validator),
 * never by this enum or by {@code Ticket} itself.
 */
public enum TicketStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    WAITING_FOR_CUSTOMER,
    RESOLVED,
    CLOSED
}
