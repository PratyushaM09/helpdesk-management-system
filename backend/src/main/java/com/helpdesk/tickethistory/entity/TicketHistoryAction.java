package com.helpdesk.tickethistory.entity;

/**
 * What kind of change a {@code TicketHistory} row records. {@code ASSIGNED}/
 * {@code REASSIGNED} are kept distinct from {@code STATUS_CHANGED} (rather
 * than folded into it) so an "assignment history" view is a simple filter
 * over this column (05-Database.md §1), even though assigning also happens
 * to move status to {@code ASSIGNED}. {@code REOPENED} is the event marker
 * for a {@code CLOSED} ticket re-entering at {@code ASSIGNED} (Phase 3
 * design review, Final Adjustment 1) - never a persisted {@code TicketStatus}
 * value.
 */
public enum TicketHistoryAction {
    CREATED,
    ASSIGNED,
    REASSIGNED,
    STATUS_CHANGED,
    PRIORITY_CHANGED,
    CATEGORY_CHANGED,
    COMMENT_ADDED,
    REOPENED,
    SOFT_DELETED,
    /** A title edit (Service Milestone 3) - one row per changed field, searchable by exactly which field changed rather than a generic "UPDATED" plus a note. */
    TITLE_UPDATED,
    /** A description edit (Service Milestone 3) - see {@link #TITLE_UPDATED}. */
    DESCRIPTION_UPDATED
}
