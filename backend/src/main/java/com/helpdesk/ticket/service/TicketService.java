package com.helpdesk.ticket.service;

import com.helpdesk.ticket.dto.request.AssignTicketRequest;
import com.helpdesk.ticket.dto.request.ChangeTicketStatusRequest;
import com.helpdesk.ticket.dto.request.CreateTicketRequest;
import com.helpdesk.ticket.dto.request.ReopenTicketRequest;
import com.helpdesk.ticket.dto.request.UpdateTicketRequest;
import com.helpdesk.ticket.dto.response.TicketDetailResponse;
import com.helpdesk.ticket.dto.response.TicketSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Ticket Service Milestone 1 (creation, single-ticket retrieval, role-scoped
 * listing), Milestone 2 (assignment, reassignment, workflow transitions,
 * reopen, soft delete), and Milestone 3 (title/description editing, plus
 * {@link #validateTicketAccess} for Comment/Attachment/TicketHistory
 * services to reuse). Comments and attachments themselves remain owned by
 * their own dedicated services.
 */
public interface TicketService {

    /**
     * Creates a new ticket for the authenticated caller. Starts {@code OPEN},
     * unassigned - the entity's own defaults, not something this method sets.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if {@code categoryId} doesn't resolve
     * @throws com.helpdesk.exception.BadRequestException       if the category is deactivated
     */
    TicketDetailResponse createTicket(CreateTicketRequest request);

    /**
     * Fetches a single ticket, enforcing role-scoped visibility (USER: own
     * only; SUPPORT_ENGINEER: assigned only; ADMIN: any).
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket doesn't exist,
     *                                                            or exists but isn't visible to the
     *                                                            caller - the two cases are never
     *                                                            distinguished
     */
    TicketDetailResponse getTicketById(Long id);

    /**
     * Lists tickets visible to the authenticated caller (USER: own only;
     * SUPPORT_ENGINEER: assigned only; ADMIN: all), paginated and sorted. No
     * additional search/filter parameters yet.
     */
    Page<TicketSummaryResponse> getTickets(Pageable pageable);

    /**
     * First-time assignment of an {@code OPEN} ticket to a support engineer.
     * ADMIN only.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket
     *         or {@code request.agentId()} doesn't resolve
     * @throws com.helpdesk.exception.BadRequestException       if the resolved user isn't a SUPPORT_ENGINEER
     * @throws com.helpdesk.exception.ConflictException         if the ticket isn't {@code OPEN}, or {@code request.version()} is stale
     * @throws com.helpdesk.exception.ForbiddenException        if the caller isn't ADMIN
     */
    TicketDetailResponse assignTicket(Long ticketId, AssignTicketRequest request);

    /**
     * Reassigns an already-assigned, still-active ticket to a different
     * support engineer, resetting its status to {@code ASSIGNED}. ADMIN
     * only. A {@code RESOLVED}/{@code CLOSED} ticket must be reopened first -
     * this method never revives one directly.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket
     *         or {@code request.agentId()} doesn't resolve
     * @throws com.helpdesk.exception.BadRequestException       if the resolved user isn't a SUPPORT_ENGINEER
     * @throws com.helpdesk.exception.ConflictException         if the ticket isn't in an active, already-assigned status, or {@code request.version()} is stale
     * @throws com.helpdesk.exception.ForbiddenException        if the caller isn't ADMIN
     */
    TicketDetailResponse reassignTicket(Long ticketId, AssignTicketRequest request);

    /**
     * Advances a ticket through the workflow graph (never {@code OPEN}<->{@code ASSIGNED},
     * which only {@link #assignTicket}/{@link #reassignTicket} perform, and
     * never the {@code RESOLVED}->{@code ASSIGNED} reopen event, which only
     * {@link #reopenTicket} performs). Legal transitions:
     * {@code ASSIGNED->IN_PROGRESS}, {@code IN_PROGRESS->WAITING_FOR_CUSTOMER},
     * {@code WAITING_FOR_CUSTOMER->IN_PROGRESS}, {@code IN_PROGRESS->RESOLVED},
     * {@code RESOLVED->CLOSED}. The transition-legality check applies to
     * every caller, including ADMIN - only who is allowed to invoke an
     * otherwise-legal transition is relaxed for ADMIN, never which
     * transitions exist.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket doesn't exist or isn't visible to the caller
     * @throws com.helpdesk.exception.ConflictException         if {@code current -> targetStatus} isn't a legal transition, or {@code request.version()} is stale
     * @throws com.helpdesk.exception.ForbiddenException        if the caller's role may not perform this specific transition
     */
    TicketDetailResponse changeStatus(Long ticketId, ChangeTicketStatusRequest request);

    /**
     * Reopens a {@code RESOLVED} ticket, moving it back to {@code ASSIGNED}
     * with its existing assigned engineer preserved - never {@code CLOSED},
     * which is not reopenable in this milestone. The ticket's creator (USER)
     * may reopen only within the 7-day window measured from
     * {@code resolvedAt}; ADMIN may reopen any {@code RESOLVED} ticket with
     * no window restriction. SUPPORT_ENGINEER may never reopen.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket doesn't exist or isn't visible to the caller
     * @throws com.helpdesk.exception.ConflictException         if the ticket isn't {@code RESOLVED}, or (for USER) the reopen window has passed
     * @throws com.helpdesk.exception.ForbiddenException        if the caller is SUPPORT_ENGINEER, or a USER who isn't this ticket's creator
     */
    TicketDetailResponse reopenTicket(Long ticketId, ReopenTicketRequest request);

    /**
     * Soft-deletes a ticket (ADR-0005) - populates {@code deletedAt}/{@code deletedBy};
     * never a hard delete. ADMIN only. The deleted ticket becomes invisible
     * to every subsequent lookup via {@code Ticket}'s own
     * {@code @SQLRestriction}, not because this method does anything further.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket doesn't exist (or was already deleted)
     * @throws com.helpdesk.exception.ForbiddenException        if the caller isn't ADMIN
     */
    void deleteTicket(Long ticketId);

    /**
     * Edits title/description only - never category, priority, status, or
     * assignment (those change through their own dedicated methods). ADMIN
     * may edit at any time; the ticket's creator (USER) may edit only while
     * {@code OPEN} or {@code WAITING_FOR_CUSTOMER} (FR-TICK-4);
     * SUPPORT_ENGINEER may never edit title/description. Records one
     * {@code TicketHistory} row per field that actually changed - none if
     * neither did.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket doesn't exist or isn't visible to the caller
     * @throws com.helpdesk.exception.ConflictException         if a USER's ticket isn't in an editable status, or {@code request.version()} is stale
     * @throws com.helpdesk.exception.ForbiddenException        if the caller is SUPPORT_ENGINEER
     */
    TicketDetailResponse updateTicket(Long ticketId, UpdateTicketRequest request);

    /**
     * Throws unless the ticket exists and is visible to the current caller -
     * the exact rule {@link #getTicketById} enforces, exposed here so
     * Comment/Attachment/TicketHistory services can reuse it rather than
     * re-deriving ticket visibility themselves (02-Architecture.md §3: this
     * is a {@code void} check, never a way to obtain the {@code Ticket}
     * entity itself - callers needing the entity resolve it via their own
     * same-domain repository access, the same pattern already used for
     * {@code User}/{@code Category}).
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket doesn't exist or isn't visible to the caller
     */
    void validateTicketAccess(Long ticketId);
}
