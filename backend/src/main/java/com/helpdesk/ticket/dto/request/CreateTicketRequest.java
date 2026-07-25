package com.helpdesk.ticket.dto.request;

import com.helpdesk.ticket.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/tickets} request body (FR-TICK-3).
 * <p>
 * Deliberately excludes {@code status}, {@code assignedTo}, {@code createdBy},
 * {@code ticketNumber}, and {@code version}: {@code status} always starts
 * {@code OPEN}, {@code ticketNumber} is Service-generated, {@code version} is
 * Hibernate-managed, and {@code createdBy} resolves from the authenticated
 * caller — none of them is ever client-supplied, the same "allow-listed by
 * construction" reasoning {@code CreateUserRequest} already applies to
 * {@code role}.
 *
 * @param title       entity column {@code title}, max 200
 * @param description entity column {@code description} (unbounded {@code TEXT},
 *                     so no {@code @Size} upper bound is imposed here)
 * @param categoryId  must reference an existing, active {@code Category} —
 *                    existence/active-state is a Service-layer check, not
 *                    expressible as Bean Validation
 * @param priority    one of {@code LOW}, {@code MEDIUM}, {@code HIGH}, {@code URGENT}
 */
public record CreateTicketRequest(
        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        String description,

        @NotNull
        Long categoryId,

        @NotNull
        TicketPriority priority
) {
}
