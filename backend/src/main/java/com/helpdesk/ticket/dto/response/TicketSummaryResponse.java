package com.helpdesk.ticket.dto.response;

import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;

import java.time.Instant;

/**
 * List-view payload for {@code GET /api/v1/tickets} (FR-TICK-9/10), wrapped
 * in {@link com.helpdesk.common.PageResponse} then
 * {@link com.helpdesk.common.ApiResponse}. Deliberately excludes
 * {@code description}/{@code createdByName}/{@code resolvedAt}/
 * {@code closedAt}/{@code version} — those are {@link TicketDetailResponse}-only,
 * keeping the list payload small across a potentially large paginated result.
 *
 * @param id             surrogate identifier
 * @param ticketNumber   human-readable identifier (FR-TICK-1)
 * @param title          entity column {@code title}
 * @param status         current {@link TicketStatus}
 * @param priority       current {@link TicketPriority}
 * @param categoryName   the ticket's category name — never the {@code Category} entity/id
 * @param assignedToName the assigned agent's display name; {@code null} while unassigned
 * @param createdAt      when the ticket was created
 * @param updatedAt      when the ticket was last modified
 */
public record TicketSummaryResponse(
        Long id,
        String ticketNumber,
        String title,
        TicketStatus status,
        TicketPriority priority,
        String categoryName,
        String assignedToName,
        Instant createdAt,
        Instant updatedAt
) {
}
