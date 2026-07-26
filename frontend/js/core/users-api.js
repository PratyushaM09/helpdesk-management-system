/**
 * User admin API calls. Thin wrappers around core/api.js only. All
 * ADMIN-only on the backend.
 */

import { api } from "./api.js";

/**
 * GET /users — paginated; only page/size/sort exist as query params on
 * this endpoint (no name/email/role/status filter or search — confirmed by
 * reading UserController/UserServiceImpl). Sortable properties: id, name,
 * email, status, createdAt, updatedAt (no `role`).
 * @param {{page?: number, size?: number, sort?: string}} [options]
 */
export async function listUsers({ page = 0, size = 10, sort } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (sort) {
    params.set("sort", sort);
  }
  return api.get(`/users?${params.toString()}`);
}

/**
 * PUT /users/{id} — name, email, and role together (role is required, not
 * optional, on this endpoint). 409 if the email is already taken by a
 * different user.
 * @param {{name: string, email: string, role: string}} request
 */
export async function updateUser(id, request) {
  return api.put(`/users/${id}`, request);
}

/**
 * PUT /account/{userId}/activate — no request body. A DELETE /users/{id}
 * route also exists and performs the identical deactivate operation
 * (UserController delegates to the same AccountService method) — using
 * the AccountController pair for both keeps activate/deactivate symmetric
 * since no activate route exists under /users at all.
 */
export async function activateUser(userId) {
  return api.put(`/account/${userId}/activate`);
}

/** PUT /account/{userId}/deactivate — no request body. Revokes all of that user's sessions server-side. */
export async function deactivateUser(userId) {
  return api.put(`/account/${userId}/deactivate`);
}
