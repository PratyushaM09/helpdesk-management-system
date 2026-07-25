/**
 * Display mappings for ticket status/priority/history-action enums — one
 * source of truth shared by tickets.js and ticket-details.js so the two
 * pages can't silently drift into different badge colors for the same
 * backend value.
 */

const STATUS_META = {
  OPEN: { label: "Open", badgeClass: "badge--info" },
  ASSIGNED: { label: "Assigned", badgeClass: "badge--brand" },
  IN_PROGRESS: { label: "In Progress", badgeClass: "badge--signal" },
  WAITING_FOR_CUSTOMER: { label: "Waiting for Customer", badgeClass: "badge--neutral" },
  RESOLVED: { label: "Resolved", badgeClass: "badge--success" },
  CLOSED: { label: "Closed", badgeClass: "badge--neutral" },
};

const PRIORITY_META = {
  LOW: { label: "Low", badgeClass: "badge--neutral" },
  MEDIUM: { label: "Medium", badgeClass: "badge--signal" },
  HIGH: { label: "High", badgeClass: "badge--danger" },
  URGENT: { label: "Urgent", badgeClass: "badge--urgent" },
};

/** Order used for the client-side "Priority (high to low)" sort — severity, not alphabetical. */
export const PRIORITY_ORDER = { URGENT: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

export const HISTORY_ACTION_LABELS = {
  CREATED: "Ticket created",
  ASSIGNED: "Assigned",
  REASSIGNED: "Reassigned",
  STATUS_CHANGED: "Status changed",
  PRIORITY_CHANGED: "Priority changed",
  CATEGORY_CHANGED: "Category changed",
  COMMENT_ADDED: "Comment added",
  REOPENED: "Reopened",
  SOFT_DELETED: "Deleted",
  TITLE_UPDATED: "Title updated",
  DESCRIPTION_UPDATED: "Description updated",
};

/** @returns {{label: string, badgeClass: string}} */
export function formatStatus(status) {
  return STATUS_META[status] ?? { label: status, badgeClass: "badge--neutral" };
}

/** @returns {{label: string, badgeClass: string}} */
export function formatPriority(priority) {
  return PRIORITY_META[priority] ?? { label: priority, badgeClass: "badge--neutral" };
}

/** @returns {string} */
export function formatHistoryAction(action) {
  return HISTORY_ACTION_LABELS[action] ?? action;
}
