package com.helpdesk.ticket.dto.request;

import com.helpdesk.ticket.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/v1/tickets/{id}/status} request body (FR-FLOW-1/2).
 * Carries only the requested target status and an optional note; whether the
 * {@code current status -> targetStatus} transition is legal for the
 * caller's role is entirely a Service-layer concern (a future workflow
 * validator) — this DTO makes no attempt to validate that itself.
 *
 * @param targetStatus the requested next {@link TicketStatus}
 * @param comment      optional free-text note recorded alongside the
 *                     transition; not length-validated (feeds a {@code TEXT}
 *                     column, same reasoning as {@code description})
 * @param version      see {@link UpdateTicketRequest#version()}
 */
public record ChangeTicketStatusRequest(
        @NotNull
        TicketStatus targetStatus,

        String comment,

        @NotNull
        Long version
) {
}
