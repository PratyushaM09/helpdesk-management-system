/**
 * Page-specific script for tickets.html. Everything here operates purely
 * on the static rows already in the DOM — filtering hides/shows rows,
 * sorting reorders them, "pagination" beyond page 1 is an inert
 * placeholder (see the buttons' data-placeholder-action). Nothing fetches
 * or generates data; the search box is intentionally left unwired (visual
 * only, per the milestone brief).
 */

import { initShell } from "../core/shell.js";
import { initFilterDropdowns } from "../core/filter-dropdown.js";

initShell();
initFilterDropdowns();

const skeleton = document.getElementById("tickets-skeleton");
const tableWrapper = document.getElementById("tickets-table-wrapper");
const tableBody = document.getElementById("tickets-table-body");
const emptyState = document.getElementById("tickets-empty-state");
const paginationSummary = document.getElementById("pagination-summary");
const clearFiltersButton = document.getElementById("clear-filters-button");

const filterState = { status: "", priority: "", category: "" };

function getRows() {
  return Array.from(tableBody.querySelectorAll("tr"));
}

function applyFilters() {
  let visibleCount = 0;

  getRows().forEach((row) => {
    const matches =
      (filterState.status === "" || row.dataset.status === filterState.status) &&
      (filterState.priority === "" || row.dataset.priority === filterState.priority) &&
      (filterState.category === "" || row.dataset.category === filterState.category);
    row.hidden = !matches;
    if (matches) {
      visibleCount += 1;
    }
  });

  const hasResults = visibleCount > 0;
  tableWrapper.hidden = !hasResults;
  emptyState.hidden = hasResults;
  paginationSummary.textContent = hasResults
    ? `Showing 1-${visibleCount} of ${visibleCount} tickets`
    : "Showing 0 of 0 tickets";
}

const PRIORITY_ORDER = { URGENT: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

const SORT_COMPARATORS = {
  newest: (a, b) => new Date(b.dataset.updated) - new Date(a.dataset.updated),
  oldest: (a, b) => new Date(a.dataset.updated) - new Date(b.dataset.updated),
  priority: (a, b) => {
    const diff = PRIORITY_ORDER[a.dataset.priority] - PRIORITY_ORDER[b.dataset.priority];
    return diff !== 0 ? diff : new Date(b.dataset.updated) - new Date(a.dataset.updated);
  },
};

function applySort(sortKey) {
  const comparator = SORT_COMPARATORS[sortKey] ?? SORT_COMPARATORS.newest;
  getRows()
    .sort(comparator)
    .forEach((row) => tableBody.appendChild(row));
}

document.addEventListener("filterchange", (event) => {
  const { filter, value } = event.detail;
  if (filter === "sort") {
    applySort(value);
    return;
  }
  if (filter in filterState) {
    filterState[filter] = value;
    applyFilters();
  }
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

clearFiltersButton?.addEventListener("click", () => {
  filterState.status = "";
  filterState.priority = "";
  filterState.category = "";

  document.querySelectorAll(".filter-dropdown[data-filter]").forEach((container) => {
    const filterName = container.dataset.filter;
    if (!(filterName in filterState)) {
      return;
    }
    const label = container.querySelector(".filter-dropdown__label");
    container.querySelectorAll(".dropdown-menu__item").forEach((item) => {
      const isAll = item.dataset.value === "";
      item.classList.toggle("is-selected", isAll);
      item.setAttribute("aria-checked", String(isAll));
      if (isAll && label) {
        label.textContent = `${container.dataset.labelPrefix}: All`;
      }
    });
  });

  applyFilters();
});

// Simulates the fetch latency a real ticket list would have — the loading
// skeleton is otherwise never seen, since the table is static.
setTimeout(() => {
  skeleton.hidden = true;
  applyFilters();
}, 600);
