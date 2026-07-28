/**
 * Thin fetch() wrapper shared by every future feature module. Handles
 * transport concerns only — no auth flow, no retries, no caching.
 *
 * Response contract: the backend wraps every 2xx body as
 * { success, message, data, timestamp } and every error body as
 * { timestamp, status, error, errorCode, message, path, validationErrors }.
 * Callers of `api.get/post/put/delete` receive just the unwrapped `data`
 * on success, and an {@link ApiError} carrying the error envelope's fields
 * on failure — never a raw Response or a raw parsed envelope.
 */

import { CONFIG } from "./config.js";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

/**
 * Registered once by core/shell.js (never imported here directly, to avoid
 * a circular dependency — shell.js already imports this module) so that a
 * 401 from any authenticated call, anywhere in the app, is handled the same
 * way: attempt one silent refresh, then redirect to login if that also
 * fails. Deliberately not triggered for /auth/* calls themselves (a plain
 * wrong-password 401 from login.html is not a "session expired" event).
 * @param {() => void} handler
 */
export function onSessionExpired(handler) {
  sessionExpiredHandler = handler;
}

let sessionExpiredHandler = null;

/**
 * The CSRF double-submit value, cached in memory only. Not read from the
 * csrf_token cookie via document.cookie, because that cookie's Domain
 * belongs to the API's origin — a frontend on a different subdomain can
 * never see it via JS, regardless of SameSite/Secure (cookie visibility to
 * JS is scoped to the setting origin, a separate axis entirely). auth.js's
 * primeCsrfToken()/login() populate this from the API's response body
 * instead (core/auth.js, GET /auth/csrf-token and POST /auth/login).
 * @param {string} token
 */
export function setCsrfToken(token) {
  csrfToken = token;
}

/**
 * Synchronous access to the cached CSRF token, for the one caller that
 * can't go through this module's own request() — attachments-api.js's
 * upload path uses XMLHttpRequest directly (for progress events), so it
 * attaches the header itself instead.
 * @returns {string|null}
 */
export function getCsrfToken() {
  return csrfToken;
}

let csrfToken = null;

export class ApiError extends Error {
  constructor(message, { status = 0, errorCode = null, validationErrors = null, path = null } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errorCode = errorCode;
    this.validationErrors = validationErrors;
    this.path = path;
  }
}

async function parseJsonSafely(response) {
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function request(method, path, body, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), options.timeoutMs ?? CONFIG.REQUEST_TIMEOUT_MS);

  const headers = new Headers(options.headers);
  if (body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  // Double-submit CSRF only applies to state-changing requests; the
  // backend's CsrfValidationFilter ignores it entirely for GET/HEAD/OPTIONS.
  if (!SAFE_METHODS.has(method) && csrfToken) {
    headers.set(CONFIG.CSRF_HEADER_NAME, csrfToken);
  }

  let response;
  try {
    response = await fetch(`${CONFIG.API_BASE_URL}${path}`, {
      method,
      headers,
      credentials: "include",
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
  } catch (error) {
    if (error.name === "AbortError") {
      throw new ApiError("The request timed out. Please try again.", { errorCode: "REQUEST_TIMEOUT" });
    }
    throw new ApiError("Unable to reach the server. Check your connection and try again.", {
      errorCode: "NETWORK_ERROR",
    });
  } finally {
    clearTimeout(timeoutId);
  }

  const payload = await parseJsonSafely(response);

  if (!response.ok) {
    const errorBody = payload ?? {};
    if (response.status === 401 && !path.startsWith("/auth/")) {
      sessionExpiredHandler?.();
    }
    throw new ApiError(errorBody.message || `Request failed with status ${response.status}`, {
      status: response.status,
      errorCode: errorBody.errorCode ?? null,
      validationErrors: errorBody.validationErrors ?? null,
      path: errorBody.path ?? path,
    });
  }

  return payload?.data ?? null;
}

export const api = {
  get: (path, options) => request("GET", path, undefined, options),
  post: (path, body, options) => request("POST", path, body, options),
  put: (path, body, options) => request("PUT", path, body, options),
  delete: (path, options) => request("DELETE", path, undefined, options),
};
