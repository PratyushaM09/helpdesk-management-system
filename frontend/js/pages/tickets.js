/**
 * Page-specific script for tickets.html. Filtering/skeleton/empty-state
 * chrome comes from core/table-filter.js (shared with users.js); sorting
 * and row-click navigation are specific to this page and stay here.
 * Nothing fetches or generates data; the search box is intentionally left
 * unwired (visual only, per the milestone brief).
 */

import { initShell } from "../core/shell.js";
import { initFilterDropdowns } from "../core/filter-dropdown.js";
import { initTableFilter } from "../core/table-filter.js";

initShell();
initFilterDropdowns();

const tableBody = document.getElementById("tickets-table-body");

const tableFilter = initTableFilter({
  tableBody,
  tableWrapper: document.getElementById("tickets-table-wrapper"),
  emptyState: document.getElementById("tickets-empty-state"),
  paginationSummary: document.getElementById("pagination-summary"),
  skeleton: document.getElementById("tickets-skeleton"),
  filterKeys: ["status", "priority", "category"],
  noun: "tickets",
});

document.getElementById("clear-filters-button")?.addEventListener("click", tableFilter.clearFilters);

const PRIORITY_ORDER = { URGENT: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

const SORT_COMPARATORS = {
  newest: (a, b) => new Date(b.dataset.updated) - new Date(a.dataset.updated),
  oldest: (a, b) => new Date(a.dataset.updated) - new Date(b.dataset.updated),
  priority: (a, b) => {
    const diff = PRIORITY_ORDER[a.dataset.priority] - PRIORITY_ORDER[b.dataset.priority];
    return diff !== 0 ? diff : new Date(b.dataset.updated) - new Date(a.dataset.updated);
  },
};

document.addEventListener("filterchange", (event) => {
  if (event.detail.filter !== "sort") {
    return;
  }
  const comparator = SORT_COMPARATORS[event.detail.value] ?? SORT_COMPARATORS.newest;
  tableFilter
    .getRows()
    .sort(comparator)
    .forEach((row) => tableBody.appendChild(row));
});

// Mouse convenience only — every row's real, keyboard-reachable entry
// point is the "View" link in its Actions cell; this just lets a click
// anywhere else on the row navigate there too.
tableBody.addEventListener("click", (event) => {
  if (event.target.closest("a, button")) {
    return;
  }
  const row = event.target.closest("tr");
  if (row?.dataset.href) {
    window.location.href = row.dataset.href;
  }
});
