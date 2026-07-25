package com.helpdesk.ticket.mapper;

import com.helpdesk.ticket.dto.request.CreateTicketRequest;
import com.helpdesk.ticket.dto.request.UpdateTicketRequest;
import com.helpdesk.ticket.dto.response.TicketDetailResponse;
import com.helpdesk.ticket.dto.response.TicketSummaryResponse;
import com.helpdesk.ticket.entity.Category;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.user.entity.User;

/**
 * Structural DTO ⇄ Entity conversion only for {@link Ticket} - no business
 * rule, no repository access. {@code ticketNumber}/{@code category}/
 * {@code createdBy} are received already-resolved (Service generates the
 * number, looks up the category, and reads the authenticated caller), the
 * same "mapper never resolves its own associations" convention
 * {@code UserMapper.toEntity} already follows for {@code Role}.
 * <p>
 * Deliberately no mapping for status transitions, timestamps,
 * {@code assignedTo}, history, or attachments - all Service responsibilities.
 * <p>
 * Declared as an interface with a hand-written {@code TicketMapperImpl}
 * (same pre-MapStruct convention as {@code UserMapper}/{@code RoleMapper}).
 */
public interface TicketMapper {

    /**
     * Builds a new, transient {@link Ticket} from a create request. Status
     * always starts {@code OPEN} (the entity's own default) - not set here.
     */
    Ticket toEntity(CreateTicketRequest request, String ticketNumber, Category category, User createdBy);

    /**
     * Applies an update request onto an already-managed {@link Ticket} in
     * place. Only {@code title}/{@code description} are touched - category,
     * priority, status, and assignment change through their own dedicated
     * requests ({@code AssignTicketRequest}/{@code ChangeTicketStatusRequest}),
     * never this one. {@code request.version()} is not applied here -
     * comparing it against the ticket's current version is a Service-layer
     * concern, and {@code Ticket.version} has no setter to apply it to
     * regardless (Hibernate-managed, ADR-0010).
     */
    void updateEntity(Ticket ticket, UpdateTicketRequest request);

    /** Projects a {@link Ticket} to its list-view response shape. */
    TicketSummaryResponse toSummaryResponse(Ticket ticket);

    /** Projects a {@link Ticket} to its detail-view response shape. */
    TicketDetailResponse toDetailResponse(Ticket ticket);
}
