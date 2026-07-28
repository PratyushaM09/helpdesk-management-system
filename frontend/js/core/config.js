/**
 * Single source of truth for environment-specific values. Nothing outside
 * this file should hardcode a URL, header name, or storage key.
 *
 * The backend's CORS allow-list (application.yml: app.cors.allowed-origins)
 * only permits http://localhost:3000 and http://localhost:5173 in dev, so
 * this frontend must currently be served from one of those two origins for
 * cookie-based auth to work.
 */
export const CONFIG = Object.freeze({
  API_BASE_URL:
    window.location.hostname === "localhost"
      ? "http://localhost:8080/api/v1"
      : "https://helpdesk-management-system-n5tt.onrender.com/api/v1",

  REQUEST_TIMEOUT_MS: 15000,

  // Matches SecurityConstants.CSRF_HEADER on the backend (SDR-007).
  CSRF_HEADER_NAME: "X-CSRF-Token",

  // ...
});
