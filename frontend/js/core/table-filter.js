/**
 * Filters a static table's rows against one or more `[data-filter]`
 * dropdowns (via the `filterchange` events `core/filter-dropdown.js`
 * dispatches), toggling the empty-state/table-wrapper and updating a
 * pagination summary — the shared shape behind tickets.js and users.js.
 * Sorting, row-click navigation, and anything else page-specific stays in
 * the page script; this only owns "which rows are currently visible."
 */

/**
 * @param {{
 *   tableBody: HTMLElement,
 *   tableWrapper?: HTMLElement,
 *   emptyState?: HTMLElement,
 *   paginationSummary?: HTMLElement,
 *   skeleton?: HTMLElement,
 *   filterKeys: string[],
 *   noun: string,
 *   skeletonDelayMs?: number
 * }} options
 */
export function initTableFilter({
  tableBody,
  tableWrapper,
  emptyState,
  paginationSummary,
  skeleton,
  filterKeys,
  noun,
  skeletonDelayMs = 600,
}) {
  const filterState = Object.fromEntries(filterKeys.map((key) => [key, ""]));

  function getRows() {
    return Array.from(tableBody.querySelectorAll("tr"));
  }

  function applyFilters() {
    let visibleCount = 0;

    getRows().forEach((row) => {
      const matches = filterKeys.every((key) => filterState[key] === "" || row.dataset[key] === filterState[key]);
      row.hidden = !matches;
      if (matches) {
        visibleCount += 1;
      }
    });

    const hasResults = visibleCount > 0;
    if (tableWrapper) {
      tableWrapper.hidden = !hasResults;
    }
    if (emptyState) {
      emptyState.hidden = hasResults;
    }
    if (paginationSummary) {
      paginationSummary.textContent = hasResults
        ? `Showing 1-${visibleCount} of ${visibleCount} ${noun}`
        : `Showing 0 of 0 ${noun}`;
    }
  }

  document.addEventListener("filterchange", (event) => {
    const { filter, value } = event.detail;
    if (filter in filterState) {
      filterState[filter] = value;
      applyFilters();
    }
  });

  function clearFilters() {
    filterKeys.forEach((key) => {
      filterState[key] = "";
    });

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
  }

  if (skeleton) {
    setTimeout(() => {
      skeleton.hidden = true;
      applyFilters();
    }, skeletonDelayMs);
  } else {
    applyFilters();
  }

  return { applyFilters, clearFilters, getRows };
}
