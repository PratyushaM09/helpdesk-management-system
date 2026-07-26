/**
 * Role API calls. Thin wrapper around core/api.js. Read-only from this
 * frontend — no create/update/delete UI exists (roles.html is display-only).
 */

import { api } from "./api.js";

/**
 * GET /roles — paginated (not a plain list) on the backend; ADMIN only.
 * There are only 3 seeded roles, so a single generously-sized page covers
 * all of them without needing pagination UI.
 * @param {{page?: number, size?: number}} [options]
 */
export async function listRoles({ page = 0, size = 50 } = {}) {
  return api.get(`/roles?page=${page}&size=${size}`);
}
