/**
 * Page-specific script for users.html. No row navigation here (there's no
 * user-details page yet) — every row action is a placeholder toast via
 * data-placeholder-action, handled globally by core/shell.js.
 */

import { initShell } from "../core/shell.js";
import { initFilterDropdowns } from "../core/filter-dropdown.js";
import { initTableFilter } from "../core/table-filter.js";

initShell();
initFilterDropdowns();

const tableFilter = initTableFilter({
  tableBody: document.getElementById("users-table-body"),
  tableWrapper: document.getElementById("users-table-wrapper"),
  emptyState: document.getElementById("users-empty-state"),
  paginationSummary: document.getElementById("user-pagination-summary"),
  skeleton: document.getElementById("users-skeleton"),
  filterKeys: ["role", "status"],
  noun: "users",
});

document.getElementById("clear-user-filters-button")?.addEventListener("click", tableFilter.clearFilters);
