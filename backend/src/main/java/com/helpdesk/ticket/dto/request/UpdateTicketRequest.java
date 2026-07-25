package com.helpdesk.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/tickets/{id}} request body — the ticket creator's own
 * pre-triage edit (FR-TICK-4), not a full ticket update. Deliberately
 * excludes {@code categoryId}/{@code priority}/{@code status}/
 * {@code assignedTo}: those are re-triage/workflow fields the creator never
 * controls directly (see {@link AssignTicketRequest}/
 * {@link ChangeTicketStatusRequest}) — the same "only customer-editable
 * fields" scoping {@code UpdateProfileRequest} already applies to a
 * self-service update.
 *
 * @param title       see {@link CreateTicketRequest#title()}
 * @param description see {@link CreateTicketRequest#description()}
 * @param version     the entity's {@code @Version} the caller last read; a
 *                     stale value is rejected as an optimistic-lock conflict
 *                     (ADR-0010), never silently overwritten
 */
public record UpdateTicketRequest(
        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        String description,

        @NotNull
        Long version
) {
}
