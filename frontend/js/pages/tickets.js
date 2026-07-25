/**
 * Page-specific script for tickets.html. Real GET /tickets with real
 * server-side pagination and sort (newest/oldest → `sort=updatedAt,...`).
 * The backend has no status/priority/category/search query params at all
 * (confirmed by reading TicketController/TicketServiceImpl), so — per this
 * milestone's own guidance — Status/Priority/Category filtering, the
 * search box, and "Priority (high to low)" sort are all client-side,
 * scoped to whichever page is currently loaded, not the full result set.
 */

import { initShell } from "../core/shell.js";
import { initFilterDropdowns } from "../core/filter-dropdown.js";
import { listTickets, getCategories } from "../core/tickets-api.js";
import { formatStatus, formatPriority, PRIORITY_ORDER } from "../core/ticket-format.js";
import { escapeHtml, formatDate, debounce } from "../core/utils.js";
import { ApiError } from "../core/api.js";

const PAGE_SIZE = 10;
const SERVER_SORTS = { newest: "updatedAt,desc", oldest: "updatedAt,asc" };

const user = await initShell();
if (user) {
  init();
}

async function init() {
  const skeleton = document.getElementById("tickets-skeleton");
  const tableWrapper = document.getElementById("tickets-table-wrapper");
  const tableBody = document.getElementById("tickets-table-body");
  const emptyState = document.getElementById("tickets-empty-state");
  const errorState = document.getElementById("tickets-error-state");
  const errorMessage = document.getElementById("tickets-error-message");
  const paginationSummary = document.getElementById("pagination-summary");
  const paginationControls = document.getElementById("pagination-controls");
  const prevButton = document.getElementById("pagination-prev");
  const nextButton = document.getElementById("pagination-next");
  const clearFiltersButton = document.getElementById("clear-filters-button");
  const searchInput = document.getElementById("ticket-search");

  let currentPage = 0;
  let totalPages = 0;
  let totalElements = 0;
  let sortMode = "newest";
  const clientFilters = { status: "", priority: "", category: "" };

  document.addEventListener("filterchange", (event) => {
    const { filter, value } = event.detail;
    if (filter === "sort") {
      sortMode = value;
      if (value === "priority") {
        sortRowsByPriority();
        applyClientFilters();
      } else {
        loadPage(0);
      }
      return;
    }
    if (filter in clientFilters) {
      clientFilters[filter] = value;
      applyClientFilters();
    }
  });

  searchInput?.addEventListener(
    "input",
    debounce(() => applyClientFilters(), 200)
  );

  clearFiltersButton?.addEventListener("click", () => {
    clientFilters.status = "";
    clientFilters.priority = "";
    clientFilters.category = "";
    if (searchInput) {
      searchInput.value = "";
    }
    resetFilterDropdownSelections();
    applyClientFilters();
  });

  prevButton.addEventListener("click", () => {
    if (currentPage > 0) {
      loadPage(currentPage - 1);
    }
  });

  nextButton.addEventListener("click", () => {
    if (currentPage < totalPages - 1) {
      loadPage(currentPage + 1);
    }
  });

  // Mouse convenience only — every row's real, keyboard-reachable entry
  // point is the "View" link in its Actions cell.
  tableBody.addEventListener("click", (event) => {
    if (event.target.closest("a, button")) {
      return;
    }
    const row = event.target.closest("tr");
    if (row?.dataset.href) {
      window.location.href = row.dataset.href;
    }
  });

  loadPage(0);

  try {
    const categories = await getCategories();
    const menu = document.getElementById("category-filter-menu");
    categories.forEach((category) => {
      const item = document.createElement("button");
      item.type = "button";
      item.className = "dropdown-menu__item";
      item.setAttribute("role", "menuitemradio");
      item.setAttribute("aria-checked", "false");
      item.dataset.value = category.name;
      item.innerHTML = `${escapeHtml(category.name)} <i class="bi bi-check2" aria-hidden="true"></i>`;
      menu.appendChild(item);
    });
  } catch {
    // Category filter just stays with "All categories" only — not fatal.
  }

  // Wired exactly once, after the category dropdown's options (above) are
  // in their final DOM state — calling this twice would double-attach
  // open/close listeners to every dropdown trigger on the page.
  initFilterDropdowns();

  async function loadPage(page) {
    skeleton.hidden = false;
    tableWrapper.hidden = true;
    emptyState.hidden = true;
    errorState.hidden = true;

    try {
      const sort = sortMode === "priority" ? SERVER_SORTS.newest : SERVER_SORTS[sortMode] ?? SERVER_SORTS.newest;
      const result = await listTickets({ page, size: PAGE_SIZE, sort });

      currentPage = result.page;
      totalPages = result.totalPages;
      totalElements = result.totalElements;

      tableBody.replaceChildren(...result.content.map(renderTicketRow));
      if (sortMode === "priority") {
        sortRowsByPriority();
      }

      skeleton.hidden = true;
      renderPaginationControls();
      applyClientFilters();
    } catch (error) {
      skeleton.hidden = true;
      errorMessage.textContent =
        error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
      errorState.hidden = false;
      paginationSummary.textContent = "";
    }
  }

  function sortRowsByPriority() {
    Array.from(tableBody.querySelectorAll("tr"))
      .sort((a, b) => PRIORITY_ORDER[a.dataset.priority] - PRIORITY_ORDER[b.dataset.priority])
      .forEach((row) => tableBody.appendChild(row));
  }

  function applyClientFilters() {
    const term = searchInput?.value.trim().toLowerCase() ?? "";
    let visibleCount = 0;

    Array.from(tableBody.querySelectorAll("tr")).forEach((row) => {
      const matches =
        (clientFilters.status === "" || row.dataset.status === clientFilters.status) &&
        (clientFilters.priority === "" || row.dataset.priority === clientFilters.priority) &&
        (clientFilters.category === "" || row.dataset.category === clientFilters.category) &&
        (term === "" || row.dataset.title.includes(term) || row.dataset.ticketNumber.includes(term));
      row.hidden = !matches;
      if (matches) {
        visibleCount += 1;
      }
    });

    tableWrapper.hidden = visibleCount === 0;
    emptyState.hidden = visibleCount > 0;

    if (totalElements === 0) {
      paginationSummary.textContent = "Showing 0 of 0 tickets";
    } else {
      const start = currentPage * PAGE_SIZE + 1;
      const end = currentPage * PAGE_SIZE + tableBody.querySelectorAll("tr").length;
      paginationSummary.textContent = `Showing ${start}-${end} of ${totalElements} tickets`;
    }
  }

  function renderPaginationControls() {
    paginationControls.querySelectorAll(".pagination__button[data-page]").forEach((button) => button.remove());

    const maxButtons = 5;
    const endPage = Math.min(totalPages - 1, Math.max(0, currentPage) + Math.floor(maxButtons / 2));
    const startPage = Math.max(0, endPage - maxButtons + 1);

    for (let page = startPage; page <= endPage; page += 1) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = page === currentPage ? "pagination__button is-active" : "pagination__button";
      button.dataset.page = String(page);
      button.textContent = String(page + 1);
      if (page === currentPage) {
        button.setAttribute("aria-current", "page");
      }
      button.addEventListener("click", () => loadPage(page));
      nextButton.before(button);
    }

    prevButton.disabled = currentPage <= 0;
    nextButton.disabled = totalPages === 0 || currentPage >= totalPages - 1;
  }

  function resetFilterDropdownSelections() {
    document.querySelectorAll(".filter-dropdown[data-filter]").forEach((container) => {
      const filterName = container.dataset.filter;
      if (!(filterName in clientFilters)) {
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
  }

  function renderTicketRow(ticket) {
    const row = document.createElement("tr");
    row.dataset.href = `ticket-details.html?id=${ticket.id}`;
    row.dataset.status = ticket.status;
    row.dataset.priority = ticket.priority;
    row.dataset.category = ticket.categoryName ?? "";
    row.dataset.title = ticket.title.toLowerCase();
    row.dataset.ticketNumber = ticket.ticketNumber.toLowerCase();

    const statusMeta = formatStatus(ticket.status);
    const priorityMeta = formatPriority(ticket.priority);

    row.innerHTML = `
      <td data-label="Ticket Number"><span class="badge badge--neutral">${escapeHtml(ticket.ticketNumber)}</span></td>
      <td data-label="Title">${escapeHtml(ticket.title)}</td>
      <td data-label="Category">${escapeHtml(ticket.categoryName ?? "—")}</td>
      <td data-label="Priority"><span class="badge ${priorityMeta.badgeClass}">${priorityMeta.label}</span></td>
      <td data-label="Status"><span class="badge ${statusMeta.badgeClass}">${statusMeta.label}</span></td>
      <td data-label="Assigned To">${escapeHtml(ticket.assignedToName ?? "Unassigned")}</td>
      <td data-label="Updated">${formatDate(ticket.updatedAt)}</td>
      <td data-label="Actions">
        <a href="ticket-details.html?id=${ticket.id}" class="btn btn--ghost btn--sm">
          View <i class="bi bi-arrow-right" aria-hidden="true"></i>
        </a>
      </td>
    `;
    return row;
  }
}
