/**
 * Reusable behavior for the authenticated app shell (sidebar + topbar).
 * Every authenticated page copies the same shell markup and calls
 * `initShell()` once — including the session guard, since "this chrome only
 * makes sense for a logged-in user" is itself a shell-level concern: every
 * page that has a sidebar/topbar must be guarded and show the real signed-in
 * user, so centralizing it here (rather than repeating a guard call in
 * every page script) is the single-implementation, not premature coupling.
 * Page-specific data (dashboard summary, ticket lists, ...) stays out of
 * this file — only "who is signed in" belongs here.
 */

import { bindPlaceholderActions, getInitials } from "./utils.js";
import { initDropdown } from "./dropdown.js";
import { requireAuth, handleSessionExpired } from "./session.js";
import { logout } from "./auth.js";
import { onSessionExpired } from "./api.js";

// Registered once per page load — this is what makes an expired/revoked
// session behave consistently everywhere, not just at initial page load.
onSessionExpired(handleSessionExpired);

const ROLE_LABELS = {
  ADMIN: { text: "Admin", badgeClass: "badge--brand" },
  SUPPORT_ENGINEER: { text: "Support Engineer", badgeClass: "badge--info" },
  USER: { text: "User", badgeClass: "badge--neutral" },
};

/**
 * @returns {Promise<object|null>} the signed-in user, or null if the guard
 *   already redirected to login.html (callers should stop rendering
 *   page-specific logic in that case).
 */
export async function initShell() {
  let user;
  try {
    user = await requireAuth();
  } catch {
    // Couldn't reach the server to verify the session — leave the static
    // shell markup as-is rather than bouncing an already-logged-in user to
    // the login screen over a connectivity blip. The page's own script can
    // still show a more specific error if it also needs the user.
    initSidebarToggle();
    initNavHighlighting();
    initUserMenu();
    initSearchFocusAnimation();
    bindPlaceholderActions();
    return null;
  }

  if (!user) {
    return null;
  }

  applyUserToTopbar(user);
  initSidebarToggle();
  initNavHighlighting();
  initUserMenu();
  initSearchFocusAnimation();
  initLogout();
  bindPlaceholderActions();
  return user;
}

function applyUserToTopbar(user) {
  const nameEl = document.querySelector(".topbar__user-name");
  const avatarEl = document.querySelector(".topbar__user-trigger .avatar");
  const roleEl = document.querySelector(".topbar__user-trigger .badge");
  const role = ROLE_LABELS[user.role] ?? { text: user.role, badgeClass: "badge--neutral" };

  if (nameEl) {
    nameEl.textContent = user.name;
  }
  if (avatarEl) {
    avatarEl.textContent = getInitials(user.name);
  }
  if (roleEl) {
    roleEl.textContent = role.text;
    roleEl.className = `badge ${role.badgeClass}`;
  }
}

/** Wires every real logout trigger (sidebar link + user-menu item) to the actual backend call. */
function initLogout() {
  document.querySelectorAll("[data-logout-action]").forEach((element) => {
    element.addEventListener("click", async () => {
      element.disabled = true;
      try {
        await logout();
      } catch {
        // Clearing client state and redirecting still happens below —
        // logout() already clears its own cache regardless of outcome, and
        // an expired access token making the logout call itself 401 doesn't
        // change the fact that the user is leaving this session either way.
      } finally {
        window.location.href = "login.html";
      }
    });
  });
}

function initSidebarToggle() {
  const toggleButton = document.getElementById("sidebar-toggle");
  const sidebar = document.getElementById("sidebar");
  const overlay = document.getElementById("shell-overlay");
  if (!toggleButton || !sidebar || !overlay) {
    return;
  }

  const openSidebar = () => {
    sidebar.classList.add("is-open");
    overlay.hidden = false;
    toggleButton.setAttribute("aria-expanded", "true");
  };

  const closeSidebar = () => {
    sidebar.classList.remove("is-open");
    overlay.hidden = true;
    toggleButton.setAttribute("aria-expanded", "false");
  };

  toggleButton.addEventListener("click", () => {
    if (sidebar.classList.contains("is-open")) {
      closeSidebar();
    } else {
      openSidebar();
    }
  });

  overlay.addEventListener("click", () => {
    closeSidebar();
    toggleButton.focus();
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && sidebar.classList.contains("is-open")) {
      closeSidebar();
      toggleButton.focus();
    }
  });
}

/**
 * Marks the sidebar link whose `data-nav` matches the current page as
 * active. A page that isn't itself a nav destination (ticket-details.html,
 * reached only via a ticket row, not the sidebar) can declare which nav
 * item it belongs to instead via `<body data-active-nav="tickets.html">`.
 */
function initNavHighlighting() {
  const activeNav = document.body.dataset.activeNav || window.location.pathname.split("/").pop() || "index.html";

  document.querySelectorAll("[data-nav]").forEach((link) => {
    const isActive = link.dataset.nav === activeNav;
    link.classList.toggle("is-active", isActive);
    if (isActive) {
      link.setAttribute("aria-current", "page");
    } else {
      link.removeAttribute("aria-current");
    }
  });
}

function initUserMenu() {
  const trigger = document.getElementById("user-menu-trigger");
  const menu = document.getElementById("user-menu");
  if (!trigger || !menu) {
    return;
  }

  const dropdown = initDropdown({ trigger, menu });

  menu.addEventListener("click", (event) => {
    if (event.target.closest(".dropdown-menu__item")) {
      dropdown.close();
    }
  });
}

/** A single, purposeful nudge on focus — not decorative, see shell.css. */
function initSearchFocusAnimation() {
  const wrapper = document.querySelector(".topbar__search");
  const input = wrapper?.querySelector(".topbar__search-input");
  if (!wrapper || !input) {
    return;
  }

  input.addEventListener("focus", () => wrapper.classList.add("is-focused"));
  input.addEventListener("blur", () => wrapper.classList.remove("is-focused"));
}
