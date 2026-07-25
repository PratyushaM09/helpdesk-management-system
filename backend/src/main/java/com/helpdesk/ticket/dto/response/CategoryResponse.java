package com.helpdesk.ticket.dto.response;

/**
 * Payload for the ticket-creation category picker (a future
 * {@code GET /api/v1/categories} endpoint). Deliberately excludes
 * {@code active}: this endpoint's Service-layer query already filters to
 * active-only categories, so the field would be redundant here — an
 * admin-facing category management view (out of scope this phase) would be
 * the one to expose it.
 *
 * @param id          surrogate identifier
 * @param name        entity column {@code name}
 * @param description entity column {@code description}
 */
public record CategoryResponse(
        Long id,
        String name,
        String description
) {
}
