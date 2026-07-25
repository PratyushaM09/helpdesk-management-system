package com.helpdesk.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/tickets/{id}/reopen} request body (FR-TICK-7). Covers
 * both reopen shapes the Ticket lifecycle allows — a {@code CLOSED} ticket
 * reopened within its window, or a {@code RESOLVED} ticket whose resolution
 * is disputed — which one applies is decided from the ticket's current
 * status in the Service layer, not by this DTO. No {@code version}: unlike
 * the other mutating requests, the reopen-window/current-status check the
 * Service layer performs already serves as its concurrency guard here.
 *
 * @param reason why the ticket is being reopened — recorded on the resulting
 *               {@code TicketHistory} row
 */
public record ReopenTicketRequest(
        @NotBlank
        String reason
) {
}
