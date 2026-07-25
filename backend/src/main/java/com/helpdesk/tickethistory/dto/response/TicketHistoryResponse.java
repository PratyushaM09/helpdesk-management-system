package com.helpdesk.tickethistory.dto.response;

import com.helpdesk.tickethistory.entity.TicketHistoryAction;

import java.time.Instant;

/**
 * Payload for {@code GET /api/v1/tickets/{id}/history} (FR-TICK-12).
 * Deliberately excludes {@code internal}: a row the caller isn't entitled to
 * see (e.g. an internal-note marker, for a USER caller) is filtered out
 * entirely by the Service layer before mapping — never returned with a flag
 * for the client to hide client-side.
 *
 * @param id        surrogate identifier
 * @param action    what kind of change this row records
 * @param oldValue  the value before the change; {@code null} where not applicable (e.g. {@code COMMENT_ADDED})
 * @param newValue  the value after the change; {@code null} where not applicable
 * @param actorName the acting user's display name
 * @param note      optional free-text context (e.g. a reopen reason)
 * @param createdAt when the change occurred
 */
public record TicketHistoryResponse(
        Long id,
        TicketHistoryAction action,
        String oldValue,
        String newValue,
        String actorName,
        String note,
        Instant createdAt
) {
}
