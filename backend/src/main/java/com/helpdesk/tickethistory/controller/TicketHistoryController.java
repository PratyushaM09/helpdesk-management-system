package com.helpdesk.tickethistory.controller;

import com.helpdesk.common.ApiResponse;
import com.helpdesk.common.PageResponse;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.tickethistory.dto.response.TicketHistoryResponse;
import com.helpdesk.tickethistory.service.TicketHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket history — read-only (Phase 3, Controller Milestone 2). No write
 * endpoint exists, matching {@link TicketHistoryService}/
 * {@code TicketHistoryRepository}'s append-only-by-construction contract
 * (ADR-0006) — history is written only by {@code TicketService} as a side
 * effect of its own operations, never directly via this controller.
 */
@Tag(name = "TicketHistory")
@RestController
@RequestMapping(ApiConstants.API_BASE_PATH + "/tickets")
public class TicketHistoryController {

    private final TicketHistoryService ticketHistoryService;

    public TicketHistoryController(TicketHistoryService ticketHistoryService) {
        this.ticketHistoryService = ticketHistoryService;
    }

    @Operation(summary = "List a ticket's history, paginated",
            description = "USER never receives internal entries; SUPPORT_ENGINEER/ADMIN receive the full trail.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paged history list")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket not found or not visible to the caller")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @GetMapping("/{ticketId}/history")
    public ResponseEntity<ApiResponse<PageResponse<TicketHistoryResponse>>> getHistory(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long ticketId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<TicketHistoryResponse> history = ticketHistoryService.getHistory(ticketId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(history)));
    }
}
