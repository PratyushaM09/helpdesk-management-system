/**
 * Page-specific script for verify-email.html. Reads ?token= from the
 * emailed link and redeems it via POST /account/verify-email — public, but
 * still a state-changing POST, so primeCsrfToken() runs first exactly like
 * session.js's bootstrapSession does (this page has no session to
 * bootstrap, but a stale csrf_token cookie could already exist in this
 * browser from an earlier login, and this page's own script context has
 * never populated the in-memory copy the way a protected page's
 * requireAuth() call would).
 */

import { verifyEmail } from "../core/account-api.js";
import { primeCsrfToken } from "../core/auth.js";

const loadingView = document.getElementById("loading-view");
const successView = document.getElementById("success-view");
const invalidView = document.getElementById("invalid-view");

const token = new URLSearchParams(window.location.search).get("token");

if (!token) {
  showInvalid();
} else {
  run();
}

async function run() {
  try {
    await primeCsrfToken();
    await verifyEmail(token);
    showSuccess();
  } catch {
    // Any failure - invalid/expired/already-used token (401), validation
    // (400), or a network blip - lands on the same invalid-link view. The
    // backend already refuses to distinguish which case it was (matches
    // the same anti-enumeration reasoning as login's generic 401 message).
    showInvalid();
  }
}

function showSuccess() {
  loadingView.hidden = true;
  invalidView.hidden = true;
  successView.hidden = false;
  successView.querySelector(".empty-state__title")?.focus();
}

function showInvalid() {
  loadingView.hidden = true;
  successView.hidden = true;
  invalidView.hidden = false;
}
