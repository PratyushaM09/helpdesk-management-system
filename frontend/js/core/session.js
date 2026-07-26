/**
 * Session orchestration on top of core/auth.js's raw API calls: deciding
 * whether a visitor is logged in, restoring an expired-but-recoverable
 * session, and guarding pages accordingly. Every authenticated page reaches
 * this indirectly via core/shell.js's initShell(); login.html calls
 * redirectIfAuthenticated() directly since it has no shell.
 */

import { ApiError } from "./api.js";
import { getCurrentUser, refreshToken } from "./auth.js";
import { showToast } from "./utils.js";

/**
 * Resolves the current session, transparently recovering from an expired
 * access token: tries GET /account/me first; on a 401 (expired/missing
 * access token), attempts exactly one silent POST /auth/refresh, then
 * retries /account/me once more. Returns the user, or null if there is no
 * recoverable session (never logged in, or the refresh token is also
 * invalid/expired/reused). Network/unexpected errors are rethrown so a
 * caller can distinguish "not logged in" from "couldn't reach the server."
 * @returns {Promise<object|null>}
 */
export async function bootstrapSession() {
  try {
    return await getCurrentUser({ forceRefetch: true });
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) {
      throw error;
    }
  }

  try {
    await refreshToken();
    return await getCurrentUser({ forceRefetch: true });
  } catch {
    return null;
  }
}

/**
 * Guards an authenticated page: resolves the session, redirecting to
 * login.html if there isn't one. Returns the user on success, or null after
 * redirecting (the caller should stop rendering page-specific logic either
 * way). Network/unexpected errors propagate — a connectivity blip shouldn't
 * bounce an already-logged-in user to the login screen; let the page decide
 * how to surface that instead.
 * @returns {Promise<object|null>}
 */
export async function requireAuth() {
  const user = await bootstrapSession();
  if (!user) {
    window.location.href = "login.html";
    return null;
  }
  return user;
}

/**
 * Guards login.html (and the other pre-login auth pages): if a session is
 * already active, redirect straight to the dashboard instead of showing the
 * form again.
 * @returns {Promise<void>}
 */
export async function redirectIfAuthenticated() {
  let user;
  try {
    user = await bootstrapSession();
  } catch {
    return;
  }
  if (user) {
    window.location.href = "dashboard.html";
  }
}

let handlingSessionExpiry = false;

/**
 * Registered with core/api.js via onSessionExpired() so a 401 from *any*
 * authenticated call — not just the page-load bootstrap — is handled the
 * same way everywhere: one silent refresh attempt, then either a toast
 * (session quietly recovered — the failed action itself still surfaces its
 * own error normally) or a redirect to login (refresh also failed, meaning
 * the session is genuinely over: expired past the refresh window, or
 * revoked by an admin deactivating/reactivating the account, which the
 * backend enforces on every request, not just at login). De-duplicates
 * itself so several requests failing at once don't trigger overlapping
 * refresh attempts or redirects.
 */
export async function handleSessionExpired() {
  if (handlingSessionExpiry) {
    return;
  }
  handlingSessionExpiry = true;
  try {
    await refreshToken();
    showToast("Your session was refreshed — please try that again.", "info");
  } catch {
    window.location.href = "login.html";
  } finally {
    handlingSessionExpiry = false;
  }
}
