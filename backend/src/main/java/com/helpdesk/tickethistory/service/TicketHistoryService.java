package com.helpdesk.tickethistory.service;

import com.helpdesk.tickethistory.dto.response.TicketHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read-only access to a ticket's audit trail (FR-TICK-12). Given its own
 * dedicated service, matching {@code CommentService}/{@code AttachmentService}
 * this same milestone - {@code TicketHistory} already has its own top-level
 * module (entity, repository, mapper, DTO, Entity Design milestone), so
 * folding its read path into {@code TicketService} instead would be the
 * inconsistent choice, not this one. Writing history remains
 * {@code TicketService}'s job alone (ADR-0006) - no create/update/delete
 * method exists here.
 */
public interface TicketHistoryService {

    /**
     * Lists a ticket's history, newest-appropriate ordering left to the
     * caller's {@code Pageable}. USER never receives {@code internal} rows;
     * SUPPORT_ENGINEER (assigned tickets only) and ADMIN (any ticket)
     * receive the full trail - filtered at the query level so pagination
     * totals stay correct.
     *
     * @throws com.helpdesk.exception.ResourceNotFoundException if the ticket doesn't exist or isn't visible to the caller
     */
    Page<TicketHistoryResponse> getHistory(Long ticketId, Pageable pageable);
}
