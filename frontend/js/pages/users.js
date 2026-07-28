/**
 * Page-specific script for users.html. Real GET /users with real server-side
 * pagination (sorted by name — the backend has no query params for this
 * endpoint beyond page/size/sort, and this page exposes no sort control, so
 * a fixed "name,asc" is used throughout). Role/status filtering and the
 * search box are client-side, scoped to whichever page is currently loaded
 * — confirmed by reading UserController/UserServiceImpl that no name/email/
 * role/status filter or search param exists on this endpoint at all.
 *
 * Add/Edit both reuse core/modal.js (the same controller ticket-details.js
 * uses) for their forms; Activate/Deactivate are single-action confirmations,
 * consistent with the confirm()-then-call pattern already used for delete
 * actions elsewhere in the app. Add User is the only admin-side path into
 * this app at all — there is no self-service registration (confirmed by
 * reading AuthenticationController/SecurityConfig: no /auth/register route
 * exists), so POST /users here is how every non-seed account comes to exist.
 */

import { initShell } from "../core/shell.js";
import { initFilterDropdowns } from "../core/filter-dropdown.js";
import { listUsers, createUser, updateUser, activateUser, deactivateUser } from "../core/users-api.js";
import {
  escapeHtml,
  formatDate,
  getInitials,
  showToast,
  setFieldError,
  setButtonLoading,
  setupPasswordToggle,
  debounce,
} from "../core/utils.js";
import { meetsAllPasswordRequirements, passwordsMatch } from "../core/validation.js";
import { initPasswordStrengthUI } from "../core/password-strength-ui.js";
import { ApiError } from "../core/api.js";
import { initModal } from "../core/modal.js";

const PAGE_SIZE = 10;
const SORT = "name,asc";

const ROLE_LABELS = {
  ADMIN: { text: "Admin", badgeClass: "badge--brand" },
  SUPPORT_ENGINEER: { text: "Support Engineer", badgeClass: "badge--info" },
  USER: { text: "User", badgeClass: "badge--neutral" },
};

const STATUS_LABELS = {
  ACTIVE: { text: "Active", badgeClass: "badge--success" },
  LOCKED: { text: "Locked", badgeClass: "badge--danger" },
  DEACTIVATED: { text: "Deactivated", badgeClass: "badge--neutral" },
};

const currentUser = await initShell();
if (currentUser) {
  init();
}

async function init() {
  const skeleton = document.getElementById("users-skeleton");
  const tableWrapper = document.getElementById("users-table-wrapper");
  const tableBody = document.getElementById("users-table-body");
  const emptyState = document.getElementById("users-empty-state");
  const errorState = document.getElementById("users-error-state");
  const errorMessage = document.getElementById("users-error-message");
  const paginationSummary = document.getElementById("user-pagination-summary");
  const paginationControls = document.getElementById("user-pagination-controls");
  const prevButton = document.getElementById("user-pagination-prev");
  const nextButton = document.getElementById("user-pagination-next");
  const clearFiltersButton = document.getElementById("clear-user-filters-button");
  const searchInput = document.getElementById("user-search");
  const addUserButton = document.getElementById("add-user-button");

  const modalOverlay = document.getElementById("action-modal-overlay");
  const modalTitle = document.getElementById("action-modal-title");
  const modalBody = document.getElementById("action-modal-body");
  const modalForm = document.getElementById("action-modal-form");
  const modalConfirm = document.getElementById("action-modal-confirm");

  const modal = initModal({
    overlay: modalOverlay,
    title: modalTitle,
    body: modalBody,
    form: modalForm,
    cancelButton: document.getElementById("action-modal-cancel"),
    closeButton: document.getElementById("action-modal-close"),
    inertTarget: document.getElementById("main-content"),
  });

  let currentPage = 0;
  let totalPages = 0;
  let totalElements = 0;
  const clientFilters = { role: "", status: "" };

  document.addEventListener("filterchange", (event) => {
    const { filter, value } = event.detail;
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
    clientFilters.role = "";
    clientFilters.status = "";
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

  addUserButton?.addEventListener("click", () => openCreateModal());

  initFilterDropdowns();
  await loadPage(0);

  async function loadPage(page) {
    skeleton.hidden = false;
    tableWrapper.hidden = true;
    emptyState.hidden = true;
    errorState.hidden = true;

    try {
      const result = await listUsers({ page, size: PAGE_SIZE, sort: SORT });

      currentPage = result.page;
      totalPages = result.totalPages;
      totalElements = result.totalElements;

      tableBody.replaceChildren(...result.content.map(renderUserRow));

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

  function applyClientFilters() {
    const term = searchInput?.value.trim().toLowerCase() ?? "";
    let visibleCount = 0;

    Array.from(tableBody.querySelectorAll("tr")).forEach((row) => {
      const matches =
        (clientFilters.role === "" || row.dataset.role === clientFilters.role) &&
        (clientFilters.status === "" || row.dataset.status === clientFilters.status) &&
        (term === "" || row.dataset.name.includes(term) || row.dataset.email.includes(term));
      row.hidden = !matches;
      if (matches) {
        visibleCount += 1;
      }
    });

    tableWrapper.hidden = visibleCount === 0;
    emptyState.hidden = visibleCount > 0;

    if (totalElements === 0) {
      paginationSummary.textContent = "Showing 0 of 0 users";
    } else {
      const start = currentPage * PAGE_SIZE + 1;
      const end = currentPage * PAGE_SIZE + tableBody.querySelectorAll("tr").length;
      paginationSummary.textContent = `Showing ${start}-${end} of ${totalElements} users`;
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
      button.setAttribute("aria-label", `Page ${page + 1}`);
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

  function renderUserRow(user) {
    const row = document.createElement("tr");
    row.dataset.role = user.role;
    row.dataset.status = user.status;
    row.dataset.name = user.name.toLowerCase();
    row.dataset.email = user.email.toLowerCase();

    const roleMeta = ROLE_LABELS[user.role] ?? { text: user.role, badgeClass: "badge--neutral" };
    const statusMeta = STATUS_LABELS[user.status] ?? { text: user.status, badgeClass: "badge--neutral" };
    const isSelf = user.id === currentUser.id;

    row.innerHTML = `
      <td data-label="Name">
        <div class="flex items-center gap-3">
          <span class="avatar avatar--sm" aria-hidden="true">${escapeHtml(getInitials(user.name))}</span>
          ${escapeHtml(user.name)}
        </div>
      </td>
      <td data-label="Email">${escapeHtml(user.email)}</td>
      <td data-label="Role"><span class="badge ${roleMeta.badgeClass}">${escapeHtml(roleMeta.text)}</span></td>
      <td data-label="Status"><span class="badge ${statusMeta.badgeClass}">${escapeHtml(statusMeta.text)}</span></td>
      <td data-label="Created">${formatDate(user.createdAt)}</td>
      <td data-label="Actions">
        <button type="button" class="icon-btn" data-user-edit aria-label="Edit ${escapeHtml(user.name)}">
          <i class="bi bi-pencil" aria-hidden="true"></i>
        </button>
        ${
          user.status === "DEACTIVATED"
            ? `<button type="button" class="icon-btn" data-user-activate aria-label="Activate ${escapeHtml(user.name)}"><i class="bi bi-check-circle" aria-hidden="true"></i></button>`
            : `<button type="button" class="icon-btn icon-btn--danger" data-user-deactivate aria-label="Deactivate ${escapeHtml(user.name)}" ${isSelf ? "disabled" : ""}><i class="bi bi-slash-circle" aria-hidden="true"></i></button>`
        }
      </td>
    `;

    row.querySelector("[data-user-edit]").addEventListener("click", () => openEditModal(user));
    row.querySelector("[data-user-activate]")?.addEventListener("click", () => runRowAction(user, activateUser, "User activated."));
    row.querySelector("[data-user-deactivate]")?.addEventListener("click", () => {
      if (window.confirm(`Deactivate ${user.name}? They will be signed out and unable to log back in until reactivated.`)) {
        runRowAction(user, deactivateUser, "User deactivated.");
      }
    });

    return row;
  }

  async function runRowAction(user, action, successMessage) {
    try {
      await action(user.id);
      showToast(successMessage, "success");
      await loadPage(currentPage);
    } catch (error) {
      showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
    }
  }

  function openCreateModal() {
    modal.open(
      "Add User",
      `
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-create-name">Name</label>
        <input class="form-control" id="modal-create-name" maxlength="100" required />
        <p class="form-error" id="modal-create-name-error" role="alert" hidden></p>
      </div>
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-create-email">Email</label>
        <input class="form-control" type="email" id="modal-create-email" maxlength="255" required />
        <p class="form-error" id="modal-create-email-error" role="alert" hidden></p>
      </div>
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-create-role">Role</label>
        <select class="form-control" id="modal-create-role" required>
          <option value="USER" selected>User</option>
          <option value="SUPPORT_ENGINEER">Support Engineer</option>
          <option value="ADMIN">Admin</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-create-password">Password</label>
        <div class="input-affix">
          <i class="bi bi-lock input-affix__icon" aria-hidden="true"></i>
          <input
            class="form-control"
            type="password"
            id="modal-create-password"
            autocomplete="new-password"
            aria-describedby="modal-create-password-error modal-create-password-requirements"
            required
          />
          <button type="button" id="modal-create-password-toggle" class="input-affix__toggle" aria-label="Show password">
            <i class="bi bi-eye" aria-hidden="true"></i>
          </button>
        </div>
        <p class="form-error" id="modal-create-password-error" role="alert" hidden></p>

        <div class="password-strength" id="modal-create-password-strength">
          <div
            class="password-strength__bar"
            id="modal-create-password-strength-bar"
            role="progressbar"
            aria-valuemin="0"
            aria-valuemax="100"
            aria-valuenow="0"
            aria-label="Password strength"
          >
            <div class="password-strength__fill" id="modal-create-password-strength-fill"></div>
          </div>
          <span class="password-strength__label" id="modal-create-password-strength-label"></span>
        </div>

        <ul class="password-requirements" id="modal-create-password-requirements" aria-label="Password requirements">
          <li class="password-requirements__item" data-requirement="length"><i class="bi bi-circle" aria-hidden="true"></i> 10-128 characters</li>
          <li class="password-requirements__item" data-requirement="uppercase"><i class="bi bi-circle" aria-hidden="true"></i> One uppercase letter</li>
          <li class="password-requirements__item" data-requirement="lowercase"><i class="bi bi-circle" aria-hidden="true"></i> One lowercase letter</li>
          <li class="password-requirements__item" data-requirement="digit"><i class="bi bi-circle" aria-hidden="true"></i> One number</li>
          <li class="password-requirements__item" data-requirement="special"><i class="bi bi-circle" aria-hidden="true"></i> One special character</li>
        </ul>
      </div>
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-create-confirm-password">Confirm password</label>
        <input class="form-control" type="password" id="modal-create-confirm-password" autocomplete="new-password" required />
        <p class="form-error" id="modal-create-confirm-password-error" role="alert" hidden></p>
      </div>
    `,
      async () => {
        const nameInput = document.getElementById("modal-create-name");
        const emailInput = document.getElementById("modal-create-email");
        const roleSelect = document.getElementById("modal-create-role");
        const passwordInput = document.getElementById("modal-create-password");
        const confirmPasswordInput = document.getElementById("modal-create-confirm-password");
        const nameError = document.getElementById("modal-create-name-error");
        const emailError = document.getElementById("modal-create-email-error");
        const passwordError = document.getElementById("modal-create-password-error");
        const confirmPasswordError = document.getElementById("modal-create-confirm-password-error");

        const name = nameInput.value.trim();
        const email = emailInput.value.trim();
        let hasError = false;

        if (!name) {
          setFieldError(nameInput, nameError, "Name is required.");
          hasError = true;
        } else {
          setFieldError(nameInput, nameError);
        }
        if (!email) {
          setFieldError(emailInput, emailError, "Email is required.");
          hasError = true;
        } else {
          setFieldError(emailInput, emailError);
        }
        if (!meetsAllPasswordRequirements(passwordInput.value)) {
          setFieldError(passwordInput, passwordError, "Password does not meet all requirements above.");
          hasError = true;
        } else {
          setFieldError(passwordInput, passwordError);
        }
        if (!passwordsMatch(passwordInput.value, confirmPasswordInput.value)) {
          setFieldError(confirmPasswordInput, confirmPasswordError, "Passwords do not match.");
          hasError = true;
        } else {
          setFieldError(confirmPasswordInput, confirmPasswordError);
        }
        if (hasError) {
          return;
        }

        setButtonLoading(modalConfirm, true, "Creating…");
        try {
          await createUser({ name, email, password: passwordInput.value, role: roleSelect.value });
          showToast("User created.", "success");
          modal.close();
          await loadPage(0);
        } catch (error) {
          if (error instanceof ApiError && error.validationErrors?.length) {
            error.validationErrors.forEach(({ field, message }) => {
              if (field === "name") {
                setFieldError(nameInput, nameError, message);
              } else if (field === "email") {
                setFieldError(emailInput, emailError, message);
              } else if (field === "password") {
                setFieldError(passwordInput, passwordError, message);
              }
            });
          } else if (error instanceof ApiError && error.status === 409) {
            setFieldError(emailInput, emailError, error.message);
          } else {
            showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
          }
        } finally {
          setButtonLoading(modalConfirm, false);
        }
      }
    );

    setupPasswordToggle(document.getElementById("modal-create-password-toggle"), document.getElementById("modal-create-password"));

    const requirementItems = new Map(
      Array.from(document.querySelectorAll("#modal-create-password-requirements [data-requirement]")).map((el) => [
        el.dataset.requirement,
        el,
      ])
    );
    initPasswordStrengthUI({
      passwordInput: document.getElementById("modal-create-password"),
      strengthWrapper: document.getElementById("modal-create-password-strength"),
      strengthBar: document.getElementById("modal-create-password-strength-bar"),
      strengthFill: document.getElementById("modal-create-password-strength-fill"),
      strengthLabel: document.getElementById("modal-create-password-strength-label"),
      requirementItems,
    });
  }

  function openEditModal(user) {
    modal.open(
      "Edit User",
      `
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-user-name">Name</label>
        <input class="form-control" id="modal-user-name" maxlength="100" value="${escapeHtml(user.name)}" required />
        <p class="form-error" id="modal-user-name-error" role="alert" hidden></p>
      </div>
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-user-email">Email</label>
        <input class="form-control" type="email" id="modal-user-email" maxlength="255" value="${escapeHtml(user.email)}" required />
        <p class="form-error" id="modal-user-email-error" role="alert" hidden></p>
      </div>
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-user-role">Role</label>
        <select class="form-control" id="modal-user-role" required>
          <option value="ADMIN" ${user.role === "ADMIN" ? "selected" : ""}>Admin</option>
          <option value="SUPPORT_ENGINEER" ${user.role === "SUPPORT_ENGINEER" ? "selected" : ""}>Support Engineer</option>
          <option value="USER" ${user.role === "USER" ? "selected" : ""}>User</option>
        </select>
      </div>
    `,
      async () => {
        const nameInput = document.getElementById("modal-user-name");
        const emailInput = document.getElementById("modal-user-email");
        const roleSelect = document.getElementById("modal-user-role");
        const nameError = document.getElementById("modal-user-name-error");
        const emailError = document.getElementById("modal-user-email-error");

        const name = nameInput.value.trim();
        const email = emailInput.value.trim();
        let hasError = false;
        if (!name) {
          setFieldError(nameInput, nameError, "Name is required.");
          hasError = true;
        } else {
          setFieldError(nameInput, nameError);
        }
        if (!email) {
          setFieldError(emailInput, emailError, "Email is required.");
          hasError = true;
        } else {
          setFieldError(emailInput, emailError);
        }
        if (hasError) {
          return;
        }

        setButtonLoading(modalConfirm, true, "Saving…");
        try {
          await updateUser(user.id, { name, email, role: roleSelect.value });
          showToast("User updated.", "success");
          modal.close();
          await loadPage(currentPage);
        } catch (error) {
          if (error instanceof ApiError && error.validationErrors?.length) {
            error.validationErrors.forEach(({ field, message }) => {
              if (field === "name") {
                setFieldError(nameInput, nameError, message);
              } else if (field === "email") {
                setFieldError(emailInput, emailError, message);
              }
            });
          } else if (error instanceof ApiError && error.status === 409) {
            setFieldError(emailInput, emailError, error.message);
          } else {
            showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
          }
        } finally {
          setButtonLoading(modalConfirm, false);
        }
      }
    );
  }
}
