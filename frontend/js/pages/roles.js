/**
 * Page-specific script for roles.html. Real GET /roles — read-only (no
 * create/update/delete UI exists; the backend has no create-role endpoint
 * either, per RoleSeeder's own comment: seeding is "the only source of Role
 * rows until a create-role endpoint exists"). Only 3 roles are ever seeded,
 * so a single page (size=50) covers all of them without pagination UI.
 *
 * The example-permissions bullet list and colored capability badges under
 * each card are static illustrative copy, not backend data — there's no
 * permissions field on RoleResponse, so they're kept as fixed UI content
 * (per this milestone's own "unless explicitly required by the UI design"
 * allowance) and looked up by role name. The per-role "assigned user count"
 * that used to be hardcoded in the footer is gone entirely: confirmed by
 * reading RoleController/RoleServiceImpl/UserRepository that no endpoint
 * exposes it (UserRepository.existsByRoleId is a boolean used only
 * internally for delete-validation) — this milestone explicitly allows
 * omitting it when unavailable rather than fabricating one.
 */

import { initShell } from "../core/shell.js";
import { listRoles } from "../core/roles-api.js";
import { escapeHtml } from "../core/utils.js";
import { ApiError } from "../core/api.js";

const ROLE_DISPLAY = {
  ADMIN: {
    label: "Admin",
    permissions: [
      "Manage users: create, update, deactivate, or reactivate any account",
      "Manage roles and categories",
      "Assign or reassign any ticket to an agent",
      "Edit, reopen, close, or delete any ticket",
    ],
    badges: ["User Management", "Role Management", "Ticket Assignment", "Full Ticket Access"],
    badgeClass: "badge--brand",
  },
  SUPPORT_ENGINEER: {
    label: "Support Engineer",
    permissions: [
      "View and comment on assigned tickets",
      "Add internal, staff-only notes",
      "Update the status of assigned tickets",
      "Upload attachments to assigned tickets",
    ],
    badges: ["Assigned Tickets", "Internal Notes", "Status Updates"],
    badgeClass: "badge--info",
  },
  USER: {
    label: "User",
    permissions: [
      "Create new support tickets",
      "View and comment on their own tickets",
      "Edit their own ticket's title and description while open",
      "Reopen or confirm-close their own resolved tickets",
    ],
    badges: ["Ticket Creation", "Own Tickets Only"],
    badgeClass: "badge--neutral",
  },
};

const user = await initShell();
if (user) {
  // GET /roles is admin-only on the backend - a non-admin landing here via
  // a direct URL (the sidebar link is already hidden for them, shell.js's
  // applyRoleVisibility) would otherwise just see a raw 403 error state.
  if (user.role !== "ADMIN") {
    window.location.href = "dashboard.html";
  } else {
    init();
  }
}

async function init() {
  const loadingEl = document.getElementById("roles-loading");
  const errorEl = document.getElementById("roles-error-state");
  const errorMessageEl = document.getElementById("roles-error-message");
  const grid = document.getElementById("roles-grid");

  try {
    const result = await listRoles({ page: 0, size: 50 });
    grid.replaceChildren(...result.content.map(renderRoleCard));
    loadingEl.hidden = true;
    grid.hidden = false;
  } catch (error) {
    loadingEl.hidden = true;
    errorMessageEl.textContent =
      error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
    errorEl.hidden = false;
  }
}

function renderRoleCard(role) {
  const display = ROLE_DISPLAY[role.name] ?? { label: role.name, permissions: [], badges: [], badgeClass: "badge--neutral" };

  const card = document.createElement("div");
  card.className = "card";
  card.innerHTML = `
    <div class="card__header">
      <h2>${escapeHtml(display.label)}</h2>
      <span class="badge badge--neutral">${role.system ? "System role" : "Custom role"}</span>
    </div>
    <div class="card__body">
      <p class="mb-4">${escapeHtml(role.description ?? "")}</p>
      ${
        display.permissions.length > 0
          ? `
      <p class="form-label mb-2">Example permissions</p>
      <ul class="flex flex-col gap-2 mb-4">
        ${display.permissions
          .map(
            (permission) => `
          <li class="flex items-center gap-2">
            <i class="bi bi-check2-circle" aria-hidden="true"></i>
            ${escapeHtml(permission)}
          </li>`
          )
          .join("")}
      </ul>
      <div class="flex flex-wrap gap-2 mb-4">
        ${display.badges.map((badge) => `<span class="badge ${display.badgeClass}">${escapeHtml(badge)}</span>`).join("")}
      </div>`
          : ""
      }
    </div>
  `;
  return card;
}
