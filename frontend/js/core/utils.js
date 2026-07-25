/**
 * Generic, reusable DOM/data helpers shared across every page. No business
 * logic, no knowledge of tickets/users/auth — anything domain-specific
 * belongs in its own module.
 */

import { CONFIG } from "./config.js";

const TOAST_ICONS = {
  success: "bi-check-circle-fill",
  danger: "bi-exclamation-octagon-fill",
  warning: "bi-exclamation-triangle-fill",
  info: "bi-info-circle-fill",
};

let toastContainerEl = null;
let globalLoadingOverlayEl = null;

function getToastContainer() {
  if (!toastContainerEl) {
    toastContainerEl = document.createElement("div");
    toastContainerEl.className = "toast-container";
    toastContainerEl.setAttribute("aria-live", "polite");
    toastContainerEl.setAttribute("aria-atomic", "true");
    document.body.appendChild(toastContainerEl);
  }
  return toastContainerEl;
}

/**
 * Shows a dismissible toast notification.
 * @param {string} message
 * @param {"success"|"danger"|"warning"|"info"} [type="info"]
 * @param {number} [duration] milliseconds before auto-dismiss; pass 0 to require manual dismissal
 */
export function showToast(message, type = "info", duration = CONFIG.TOAST_DEFAULT_DURATION_MS) {
  const container = getToastContainer();

  const toast = document.createElement("div");
  toast.className = `toast toast--${type}`;
  toast.setAttribute("role", type === "danger" ? "alert" : "status");

  const icon = document.createElement("i");
  icon.className = `bi ${TOAST_ICONS[type] ?? TOAST_ICONS.info}`;
  icon.setAttribute("aria-hidden", "true");

  const text = document.createElement("span");
  text.className = "flex-1";
  text.textContent = message;

  const dismiss = document.createElement("button");
  dismiss.type = "button";
  dismiss.className = "modal__close";
  dismiss.setAttribute("aria-label", "Dismiss notification");
  dismiss.innerHTML = '<i class="bi bi-x-lg" aria-hidden="true"></i>';
  dismiss.addEventListener("click", () => toast.remove());

  toast.append(icon, text, dismiss);
  container.appendChild(toast);

  if (duration > 0) {
    setTimeout(() => toast.remove(), duration);
  }

  return toast;
}

/**
 * Shows a loading indicator. With no argument, blocks the full viewport
 * behind a fixed overlay. Pass an element (or selector) that already has
 * `position: relative` to scope the overlay to just that container instead.
 * @param {Element|string} [target]
 */
export function showLoading(target) {
  const host = typeof target === "string" ? document.querySelector(target) : target;

  const overlay = document.createElement("div");
  overlay.className = "loading-overlay";
  if (host) {
    overlay.style.position = "absolute";
  }
  overlay.innerHTML = '<div class="spinner spinner--lg" role="status"><span class="sr-only">Loading…</span></div>';

  if (host) {
    host.dataset.loadingOverlay = "true";
    host.appendChild(overlay);
  } else {
    if (globalLoadingOverlayEl) {
      globalLoadingOverlayEl.remove();
    }
    globalLoadingOverlayEl = overlay;
    document.body.appendChild(overlay);
  }
}

/**
 * Hides a loading indicator previously shown by {@link showLoading}. Pass
 * the same target (or omit for the full-page overlay) to remove it.
 * @param {Element|string} [target]
 */
export function hideLoading(target) {
  const host = typeof target === "string" ? document.querySelector(target) : target;

  if (host) {
    host.querySelector(":scope > .loading-overlay")?.remove();
    delete host.dataset.loadingOverlay;
    return;
  }

  globalLoadingOverlayEl?.remove();
  globalLoadingOverlayEl = null;
}

/**
 * Returns a debounced wrapper around `fn` that waits `delayMs` of silence
 * before invoking the latest call.
 * @param {Function} fn
 * @param {number} [delayMs=300]
 */
export function debounce(fn, delayMs = 300) {
  let timeoutId;
  return function debounced(...args) {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => fn.apply(this, args), delayMs);
  };
}

/**
 * Formats a Date, ISO string, or epoch millis into a readable string.
 * @param {Date|string|number} date
 * @param {Intl.DateTimeFormatOptions} [options]
 */
export function formatDate(date, options) {
  const value = date instanceof Date ? date : new Date(date);
  if (Number.isNaN(value.getTime())) {
    return "";
  }
  const formatOptions = options ?? {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  };
  return new Intl.DateTimeFormat(undefined, formatOptions).format(value);
}

/**
 * Escapes HTML-significant characters so untrusted text can be safely
 * interpolated into innerHTML.
 * @param {string} value
 */
export function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

/**
 * Reads a cookie value by name (used for the double-submit CSRF token).
 * @param {string} name
 * @returns {string|null}
 */
export function getCookie(name) {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Sets or clears a field-level validation error: toggles the input's
 * `aria-invalid`, and shows/hides the associated error element (expected to
 * already be wired via that input's `aria-describedby`).
 * @param {HTMLElement} input
 * @param {HTMLElement} errorElement
 * @param {string} [message] omit or pass "" to clear the error
 */
export function setFieldError(input, errorElement, message = "") {
  const hasError = message.length > 0;
  input?.setAttribute("aria-invalid", String(hasError));
  if (errorElement) {
    errorElement.textContent = message;
    errorElement.hidden = !hasError;
  }
}

/**
 * Toggles a button between its normal label and a spinner + loading label,
 * disabling it either way so it can't be double-submitted. Restores the
 * exact original markup (icons included) when turned back off.
 * @param {HTMLButtonElement} button
 * @param {boolean} isLoading
 * @param {string} [loadingLabel="Please wait…"]
 */
export function setButtonLoading(button, isLoading, loadingLabel = "Please wait…") {
  if (!button) {
    return;
  }

  if (isLoading) {
    if (button.dataset.originalHtml === undefined) {
      button.dataset.originalHtml = button.innerHTML;
    }
    button.disabled = true;
    button.setAttribute("aria-busy", "true");
    button.innerHTML = `<span class="btn__spinner" aria-hidden="true"></span>${escapeHtml(loadingLabel)}`;
    return;
  }

  button.disabled = false;
  button.removeAttribute("aria-busy");
  if (button.dataset.originalHtml !== undefined) {
    button.innerHTML = button.dataset.originalHtml;
  }
}
