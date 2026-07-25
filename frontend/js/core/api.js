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
import { getCookie } from "./utils.js";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

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
  if (!SAFE_METHODS.has(method)) {
    const csrfToken = getCookie(CONFIG.CSRF_COOKIE_NAME);
    if (csrfToken) {
      headers.set(CONFIG.CSRF_HEADER_NAME, csrfToken);
    }
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
