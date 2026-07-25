/**
 * Ticket module API calls. Thin wrappers around core/api.js only — no
 * fetch logic of its own, no page/DOM knowledge. Every path/field name
 * here matches the backend exactly (TicketController/CategoryController);
 * see each function's endpoint comment.
 */

import { api } from "./api.js";

/**
 * GET /tickets — paginated, backend-supported params are page/size/sort
 * only (no status/priority/category/search query params exist on this
 * endpoint). Sortable properties: id, ticketNumber, title, status,
 * priority, createdAt, updatedAt. Results are already role-scoped
 * server-side (USER: own tickets, SUPPORT_ENGINEER: assigned-to-them,
 * ADMIN: all).
 * @param {{page?: number, size?: number, sort?: string}} [options]
 * @returns {Promise<{content: object[], page: number, size: number, totalElements: number, totalPages: number}>}
 */
export async function listTickets({ page = 0, size = 10, sort } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (sort) {
    params.set("sort", sort);
  }
  return api.get(`/tickets?${params.toString()}`);
}

/** GET /tickets/{id} — 404 for both "doesn't exist" and "not visible to you," deliberately indistinguishable. */
export async function getTicket(id) {
  return api.get(`/tickets/${id}`);
}

/**
 * POST /tickets — USER only on the backend.
 * @param {{title: string, description: string, categoryId: number, priority: string}} request
 */
export async function createTicket(request) {
  return api.post("/tickets", request);
}

/**
 * PUT /tickets/{id} — title/description only; `version` is optimistic-lock
 * echo (409 CONFLICT if stale).
 * @param {{title: string, description: string, version: number}} request
 */
export async function updateTicket(id, request) {
  return api.put(`/tickets/${id}`, request);
}

/** POST /tickets/{id}/assign — ADMIN only; ticket must currently be OPEN. */
export async function assignTicket(id, { agentId, version }) {
  return api.post(`/tickets/${id}/assign`, { agentId, version });
}

/** POST /tickets/{id}/reassign — ADMIN only; ticket must be ASSIGNED/IN_PROGRESS/WAITING_FOR_CUSTOMER. */
export async function reassignTicket(id, { agentId, version }) {
  return api.post(`/tickets/${id}/reassign`, { agentId, version });
}

/**
 * POST /tickets/{id}/status — legal transitions are enforced server-side
 * (ASSIGNED→IN_PROGRESS, IN_PROGRESS→WAITING_FOR_CUSTOMER|RESOLVED,
 * WAITING_FOR_CUSTOMER→IN_PROGRESS, RESOLVED→CLOSED only); anything else
 * is a 409 CONFLICT.
 * @param {{targetStatus: string, comment?: string, version: number}} request
 */
export async function changeTicketStatus(id, request) {
  return api.post(`/tickets/${id}/status`, request);
}

/**
 * POST /tickets/{id}/reopen — USER (creator only) or ADMIN; only a
 * RESOLVED ticket, and only within 7 days of resolution for a USER caller
 * (ADMIN bypasses the window). No `version` field on this endpoint.
 * @param {{reason: string}} request
 */
export async function reopenTicket(id, request) {
  return api.post(`/tickets/${id}/reopen`, request);
}

/** DELETE /tickets/{id} — ADMIN only; soft-delete. */
export async function deleteTicket(id) {
  return api.delete(`/tickets/${id}`);
}

/**
 * GET /tickets/{ticketId}/history — paginated; ascending by `id` by
 * default on the backend, so pass `sort` explicitly for a specific order.
 */
export async function listTicketHistory(ticketId, { page = 0, size = 100, sort } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (sort) {
    params.set("sort", sort);
  }
  return api.get(`/tickets/${ticketId}/history?${params.toString()}`);
}

let cachedCategories = null;

/**
 * GET /categories — a small, non-paginated, already-active-only list.
 * Cached in memory after the first successful load, per this milestone's
 * explicit requirement; pass forceRefetch to bypass it.
 * @param {{ forceRefetch?: boolean }} [options]
 * @returns {Promise<{id: number, name: string, description: string}[]>}
 */
export async function getCategories({ forceRefetch = false } = {}) {
  if (cachedCategories && !forceRefetch) {
    return cachedCategories;
  }
  cachedCategories = await api.get("/categories");
  return cachedCategories;
}
