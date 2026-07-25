package com.helpdesk.ticket.dto.response;

import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;

import java.time.Instant;

/**
 * Detail-view payload for {@code GET /api/v1/tickets/{id}}. A flat,
 * standalone record — not a composition of {@link TicketSummaryResponse} —
 * consistent with how {@code UserResponse}/{@code RoleResponse} are already
 * flat records in this codebase rather than nested DTOs.
 *
 * @param id             surrogate identifier
 * @param ticketNumber   human-readable identifier (FR-TICK-1)
 * @param title          entity column {@code title}
 * @param description    entity column {@code description}
 * @param status         current {@link TicketStatus}
 * @param priority       current {@link TicketPriority}
 * @param categoryName   the ticket's category name
 * @param createdByName  the ticket creator's display name
 * @param assignedToName the assigned agent's display name; {@code null} while unassigned
 * @param resolvedAt     when the ticket most recently entered {@code RESOLVED}; {@code null} until then
 * @param closedAt       when the ticket most recently entered {@code CLOSED}; {@code null} until then
 * @param version        the entity's {@code @Version} (ADR-0010) — the caller
 *                       must echo this back on the next mutating request
 * @param createdAt      when the ticket was created
 * @param updatedAt      when the ticket was last modified
 */
public record TicketDetailResponse(
        Long id,
        String ticketNumber,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        String categoryName,
        String createdByName,
        String assignedToName,
        Instant resolvedAt,
        Instant closedAt,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
