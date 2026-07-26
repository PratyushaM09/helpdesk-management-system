/**
 * Authenticated account-self-service API calls (as opposed to core/auth.js,
 * which covers login/logout/refresh/session-bootstrap). Thin wrappers
 * around core/api.js only.
 */

import { api } from "./api.js";

/**
 * PUT /account/profile — name only (email/role/status aren't editable from
 * this endpoint). Returns the updated AccountProfileResponse.
 * @param {{name: string}} request
 */
export async function updateProfile(request) {
  return api.put("/account/profile", request);
}

/**
 * PUT /account/password — currentPassword/newPassword/confirmPassword.
 * Succeeding revokes every refresh token for this user, including the
 * current session's — the caller must treat success as "now logged out"
 * and redirect to login, not assume the session still works.
 * @param {{currentPassword: string, newPassword: string, confirmPassword: string}} request
 */
export async function changePassword(request) {
  return api.put("/account/password", request);
}
