/**
 * Authentication module. The frontend never stores a token itself — every
 * call here relies on the httpOnly access_token/refresh_token cookies the
 * backend sets/rotates/clears. The one exception is the CSRF double-submit
 * value: the csrf_token cookie's Domain belongs to the API's origin, which a
 * frontend on a different subdomain can never read via document.cookie
 * (cookie visibility to JS is scoped to the setting origin, independent of
 * SameSite) — so it travels once in a JSON response body instead
 * (primeCsrfToken/login below) and is cached in memory via api.js's
 * setCsrfToken, which echoes it back as X-CSRF-Token on later requests. The
 * only other client-side state is an in-memory cache of the last-known user
 * profile, intentionally never persisted (no localStorage/sessionStorage) —
 * a hard refresh always re-derives it from the cookie jar via
 * getCurrentUser({ forceRefetch: true }).
 */

import { api, setCsrfToken } from "./api.js";

let cachedUser = null;

/**
 * (Re)populates the in-memory CSRF token via GET /auth/csrf-token. Every
 * page here is a separate script context (no client-side router), so this
 * runs on every page load — same reasoning as getCurrentUser's
 * forceRefetch on session bootstrap. Safe to call regardless of login
 * state; the endpoint is public and merely rotates the csrf_token cookie.
 * @returns {Promise<void>}
 */
export async function primeCsrfToken() {
  const { csrfToken } = await api.get("/auth/csrf-token");
  setCsrfToken(csrfToken);
}

/**
 * Logs a user in with email/password via POST /auth/login. On success the
 * backend sets access_token, refresh_token, and csrf_token cookies and this
 * resolves with { id, name, email, role } (no status/emailVerified — see
 * getCurrentUser for the full profile). The response's csrfToken field is
 * cached via setCsrfToken and stripped out before caching the rest as
 * cachedUser — callers of getCachedUser()/getCurrentUser() should never see
 * it. Throws ApiError on failure: 401 for invalid credentials, 423 for a
 * locked account.
 * @param {{ email: string, password: string }} credentials
 * @returns {Promise<{id:number,name:string,email:string,role:string}>}
 */
export async function login({ email, password }) {
  const { csrfToken, ...user } = await api.post("/auth/login", { email, password });
  setCsrfToken(csrfToken);
  cachedUser = user;
  return user;
}

/**
 * Logs the current user out via POST /auth/logout, clearing all auth
 * cookies server-side. Idempotent on the backend. The in-memory cache is
 * always cleared, even if the request itself fails (e.g. the access token
 * had already expired) — there is nothing meaningful left to cache either way.
 * @returns {Promise<void>}
 */
export async function logout() {
  try {
    await api.post("/auth/logout");
  } finally {
    cachedUser = null;
  }
}

/**
 * Rotates the current refresh/access token pair via POST /auth/refresh,
 * using the httpOnly refresh_token cookie the browser attaches
 * automatically (its Path is scoped to this endpoint only). Throws ApiError
 * (401) if the refresh token is missing, expired, or already rotated —
 * callers should treat that as "session is over," not "try again."
 * @returns {Promise<{id:number,name:string,email:string,role:string}>}
 */
export async function refreshToken() {
  const user = await api.post("/auth/refresh");
  cachedUser = user;
  return user;
}

/**
 * Returns the full current-user profile via GET /account/me — the only
 * endpoint that carries `status`/`emailVerified` (the login/refresh
 * response shape omits both). Cached in memory after the first successful
 * call; pass forceRefetch to bypass the cache (session bootstrap always does).
 * @param {{ forceRefetch?: boolean }} [options]
 * @returns {Promise<{id:number,name:string,email:string,role:string,status:string,emailVerified:boolean,createdAt:string}>}
 */
export async function getCurrentUser({ forceRefetch = false } = {}) {
  if (cachedUser && !forceRefetch) {
    return cachedUser;
  }
  cachedUser = await api.get("/account/me");
  return cachedUser;
}

/** Synchronous access to the last-fetched user, or null if none is cached. */
export function getCachedUser() {
  return cachedUser;
}

/** Synchronous access to the last-fetched user's role, or null. */
export function getCurrentRole() {
  return cachedUser?.role ?? null;
}
