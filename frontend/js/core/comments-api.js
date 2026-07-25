/**
 * Comment API calls for a ticket. Thin wrappers around core/api.js only.
 */

import { api } from "./api.js";

/**
 * GET /tickets/{ticketId}/comments — paginated; the backend already
 * filters INTERNAL comments out entirely for a USER caller (not just
 * hidden client-side).
 */
export async function listComments(ticketId, { page = 0, size = 20 } = {}) {
  return api.get(`/tickets/${ticketId}/comments?page=${page}&size=${size}`);
}

/**
 * POST /tickets/{ticketId}/comments. `visibility` omitted defaults to
 * PUBLIC server-side; a USER passing "INTERNAL" is rejected with a 403
 * (never silently downgraded) — see CommentServiceImpl.resolveVisibility.
 * @param {{content: string, visibility?: "PUBLIC"|"INTERNAL"}} request
 */
export async function addComment(ticketId, request) {
  return api.post(`/tickets/${ticketId}/comments`, request);
}

/** PUT /comments/{commentId} — content only; author-or-ADMIN, enforced server-side. */
export async function updateComment(commentId, { content }) {
  return api.put(`/comments/${commentId}`, { content });
}

/** DELETE /comments/{commentId} — author-or-ADMIN, enforced server-side. Physical delete. */
export async function deleteComment(commentId) {
  return api.delete(`/comments/${commentId}`);
}
