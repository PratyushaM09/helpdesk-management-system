package com.helpdesk.ticket.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/v1/tickets/{id}/assign} request body — ADMIN-only
 * assign/reassign (FR-TICK-8). No other field: moving the ticket to
 * {@code ASSIGNED} is a Service-layer side effect of this call, never
 * something the client states directly.
 *
 * @param agentId must reference an existing {@code SUPPORT_ENGINEER}-role
 *                user — a Service-layer check, not expressible as Bean
 *                Validation
 * @param version see {@link UpdateTicketRequest#version()}
 */
public record AssignTicketRequest(
        @NotNull
        Long agentId,

        @NotNull
        Long version
) {
}
