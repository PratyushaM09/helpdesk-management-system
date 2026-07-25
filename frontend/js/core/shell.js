/**
 * Reusable behavior for the authenticated app shell (sidebar + topbar).
 * Every future authenticated page (tickets, profile, admin, ...) copies the
 * same shell markup and calls `initShell()` once — no page-specific logic
 * lives here, matching the shared vs. page-script split every other core
 * module follows.
 */

import { showToast } from "./utils.js";

export function initShell() {
  initSidebarToggle();
  initNavHighlighting();
  initUserMenu();
  initSearchFocusAnimation();
  initPlaceholderActions();
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

/** Marks the sidebar link whose `data-nav` matches the current page as active. */
function initNavHighlighting() {
  const currentPage = window.location.pathname.split("/").pop() || "index.html";

  document.querySelectorAll("[data-nav]").forEach((link) => {
    const isActive = link.dataset.nav === currentPage;
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

  const openMenu = () => {
    menu.hidden = false;
    trigger.setAttribute("aria-expanded", "true");
  };

  const closeMenu = () => {
    menu.hidden = true;
    trigger.setAttribute("aria-expanded", "false");
  };

  trigger.addEventListener("click", (event) => {
    event.stopPropagation();
    if (menu.hidden) {
      openMenu();
    } else {
      closeMenu();
    }
  });

  menu.addEventListener("click", (event) => {
    if (event.target.closest(".dropdown-menu__item")) {
      closeMenu();
    }
  });

  document.addEventListener("click", (event) => {
    if (!menu.hidden && event.target !== trigger && !menu.contains(event.target) && !trigger.contains(event.target)) {
      closeMenu();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !menu.hidden) {
      closeMenu();
      trigger.focus();
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

/** Every action in the shell that has no backend yet surfaces the same kind of toast. */
function initPlaceholderActions() {
  document.getElementById("notification-button")?.addEventListener("click", () => {
    showToast("Notifications aren't wired up yet.", "info");
  });

  document.querySelectorAll("[data-logout-trigger]").forEach((element) => {
    element.addEventListener("click", () => {
      showToast("Sign-out isn't connected yet — this is the Milestone 3 UI shell.", "info");
    });
  });
}
