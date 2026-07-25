/**
 * Wires every `[data-filter]` dropdown on the page (Status/Priority/
 * Category/Sort on tickets.html today) into a working single-select menu,
 * built on top of core/dropdown.js's open/close mechanics. Reusable by any
 * future list page (My Tickets, Admin Users, Admin Roles, ...) that needs
 * the same filter-bar pattern.
 *
 * Expected markup per filter:
 *   <div class="filter-dropdown" data-filter="status" data-label-prefix="Status">
 *     <button class="filter-dropdown__trigger" aria-haspopup="true"
 *             aria-expanded="false" aria-controls="...">
 *       <span class="filter-dropdown__label">Status: All</span>
 *       <i class="bi bi-chevron-down"></i>
 *     </button>
 *     <div class="dropdown-menu" id="..." role="menu" hidden>
 *       <button class="dropdown-menu__item is-selected" role="menuitemradio"
 *               aria-checked="true" data-value="">All statuses
 *         <i class="bi bi-check2"></i></button>
 *       ...
 *     </div>
 *   </div>
 *
 * Dispatches a `filterchange` CustomEvent on the `[data-filter]` container
 * with `{ filter, value }` in `event.detail` whenever a selection changes.
 */

import { initDropdown } from "./dropdown.js";

export function initFilterDropdowns(root = document) {
  const filters = [];

  root.querySelectorAll("[data-filter]").forEach((container) => {
    const trigger = container.querySelector(".filter-dropdown__trigger");
    const menu = container.querySelector(".dropdown-menu");
    const label = container.querySelector(".filter-dropdown__label");
    if (!trigger || !menu || !label) {
      return;
    }

    const dropdown = initDropdown({ trigger, menu });
    const items = Array.from(menu.querySelectorAll(".dropdown-menu__item"));
    let selectedValue = items.find((item) => item.getAttribute("aria-checked") === "true")?.dataset.value ?? "";

    const labelPrefix = container.dataset.labelPrefix;

    items.forEach((item) => {
      item.addEventListener("click", () => {
        selectedValue = item.dataset.value ?? "";
        label.textContent = labelPrefix ? `${labelPrefix}: ${item.textContent.trim()}` : item.textContent.trim();

        items.forEach((candidate) => {
          const isSelected = candidate === item;
          candidate.classList.toggle("is-selected", isSelected);
          candidate.setAttribute("aria-checked", String(isSelected));
        });

        dropdown.close();
        container.dispatchEvent(
          new CustomEvent("filterchange", {
            bubbles: true,
            detail: { filter: container.dataset.filter, value: selectedValue },
          })
        );
      });
    });

    filters.push({ name: container.dataset.filter, getValue: () => selectedValue });
  });

  return filters;
}
