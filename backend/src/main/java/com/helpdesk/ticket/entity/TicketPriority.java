package com.helpdesk.ticket.entity;

/** Fixed by the specification (FR-PRI-1), not admin-configurable — an enum, not a table (05-Database.md §8). */
public enum TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
