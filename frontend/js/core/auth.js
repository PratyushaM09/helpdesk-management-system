/**
 * Authentication module — FOUNDATION ONLY.
 *
 * These four functions are the complete surface area the rest of the app
 * will call once auth is wired up (Milestone 2): every stub already names
 * its backend endpoint and cookie behavior so future implementation is a
 * fill-in, not a redesign.
 *
 * Backend contract these will integrate with (do not change without
 * updating the backend): POST /auth/login, POST /auth/refresh,
 * POST /auth/logout all set/clear the httpOnly access_token and
 * refresh_token cookies plus the readable csrf_token cookie — the frontend
 * never stores a token itself, it only relies on the cookie jar and
 * mirrors csrf_token into the X-CSRF-Token header (see api.js).
 */

/**
 * Logs a user in with email/password via POST /auth/login.
 * On success the backend sets access_token, refresh_token, and csrf_token
 * cookies; this function will resolve with the logged-in user's profile.
 * @param {{ email: string, password: string, rememberMe?: boolean }} credentials
 * @returns {Promise<object>} the authenticated user
 */
export async function login(credentials) {
  throw new Error("auth.login() is not implemented yet — Milestone 1 is UI/foundation only.");
}

/**
 * Logs the current user out via POST /auth/logout, clearing all auth
 * cookies server-side. Idempotent on the backend, safe to call even if the
 * session already expired.
 * @returns {Promise<void>}
 */
export async function logout() {
  throw new Error("auth.logout() is not implemented yet — Milestone 1 is UI/foundation only.");
}

/**
 * Rotates the current refresh/access token pair via POST /auth/refresh,
 * using the httpOnly refresh_token cookie the browser sends automatically.
 * Intended to be called transparently by the API layer on a 401, not
 * directly by page code.
 * @returns {Promise<void>}
 */
export async function refreshToken() {
  throw new Error("auth.refreshToken() is not implemented yet — Milestone 1 is UI/foundation only.");
}

/**
 * Returns the currently authenticated user, or null if no session exists.
 * Will back onto a "whoami"-style endpoint / cached profile once the auth
 * module lands.
 * @returns {Promise<object|null>}
 */
export async function getCurrentUser() {
  throw new Error("auth.getCurrentUser() is not implemented yet — Milestone 1 is UI/foundation only.");
}
