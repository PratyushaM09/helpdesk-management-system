package com.helpdesk.ticket.controller;

import com.helpdesk.common.ApiResponse;
import com.helpdesk.common.PageResponse;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.constant.ResponseMessages;
import com.helpdesk.ticket.dto.request.AssignTicketRequest;
import com.helpdesk.ticket.dto.request.ChangeTicketStatusRequest;
import com.helpdesk.ticket.dto.request.CreateTicketRequest;
import com.helpdesk.ticket.dto.request.ReopenTicketRequest;
import com.helpdesk.ticket.dto.request.UpdateTicketRequest;
import com.helpdesk.ticket.dto.response.TicketDetailResponse;
import com.helpdesk.ticket.dto.response.TicketSummaryResponse;
import com.helpdesk.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Ticket Domain (Phase 3, Controller Milestone 1) — every method here only
 * binds, validates via {@code @Valid}, delegates to {@link TicketService},
 * and shapes the response, the same "Controller only binds and shapes"
 * convention {@code RoleController}/{@code UserController}/
 * {@code AccountController} already follow. No business rule — ownership,
 * workflow-transition legality, ticket-number generation, history writing —
 * is decided in this class; all of it already lives in
 * {@code TicketServiceImpl}.
 * <p>
 * {@code @PreAuthorize} here is deliberately coarse (which roles may even
 * attempt an operation) — fine-grained rules (is this caller's own ticket,
 * is this transition legal from the current status, is the reopen window
 * still open) are enforced inside the Service layer regardless of what a
 * caller's role would otherwise permit.
 * <p>
 * Comment/Attachment/TicketHistory endpoints are a later milestone —
 * deliberately absent here.
 */
@Tag(name = "Ticket")
@RestController
@RequestMapping(ApiConstants.API_BASE_PATH + "/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "Create a ticket")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Ticket created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a USER")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public ResponseEntity<ApiResponse<TicketDetailResponse>> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        TicketDetailResponse created = ticketService.createTicket(request);
        URI location = URI.create(ApiConstants.API_BASE_PATH + "/tickets/" + created.id());
        return ResponseEntity.created(location).body(ApiResponse.success(ResponseMessages.CREATED, created));
    }

    @Operation(summary = "Get a ticket by id",
            description = "Visibility is role-scoped (own/assigned/all) - not visible to the caller is reported identically to not found.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ticket found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket not found or not visible to the caller")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> getTicket(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.getTicketById(id)));
    }

    @Operation(summary = "List tickets, paginated",
            description = "Role-scoped: USER sees own tickets, SUPPORT_ENGINEER sees assigned tickets, ADMIN sees all.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paged ticket list")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TicketSummaryResponse>>> getTickets(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<TicketSummaryResponse> tickets = ticketService.getTickets(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(tickets)));
    }

    @Operation(summary = "Update a ticket's title/description",
            description = "Category, priority, status, and assignment never change here. "
                    + "The ticket's creator may edit only while OPEN or WAITING_FOR_CUSTOMER; an administrator may edit at any time.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ticket updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is a SUPPORT_ENGINEER")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket not found or not visible to the caller")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Ticket not in an editable state, or a stale version")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> updateTicket(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request) {
        TicketDetailResponse updated = ticketService.updateTicket(id, request);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.UPDATED, updated));
    }

    @Operation(summary = "Assign an OPEN ticket to a support engineer", description = "Administrator only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ticket assigned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed, or the target user isn't a support engineer")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an administrator")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket or agent not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Ticket isn't OPEN, or a stale version")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> assignTicket(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request) {
        TicketDetailResponse updated = ticketService.assignTicket(id, request);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.UPDATED, updated));
    }

    @Operation(summary = "Reassign an already-assigned ticket to a different support engineer", description = "Administrator only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ticket reassigned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed, or the target user isn't a support engineer")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an administrator")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket or agent not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Ticket isn't in an active, already-assigned status, or a stale version")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reassign")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> reassignTicket(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request) {
        TicketDetailResponse updated = ticketService.reassignTicket(id, request);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.UPDATED, updated));
    }

    @Operation(summary = "Transition a ticket's status",
            description = "Support engineer (own assigned ticket) or administrator for most transitions; "
                    + "the ticket's creator (USER) may additionally confirm closure of their own RESOLVED ticket.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status changed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is a USER attempting a transition other than closing, or a SUPPORT_ENGINEER attempting to close the ticket")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket not found or not visible to the caller")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Illegal transition, or a stale version")
    // Deliberately includes USER, even though most transitions are
    // SUPPORT_ENGINEER/ADMIN-only - TicketServiceImpl.validateStatusChangeActor
    // permits a USER to close their own RESOLVED ticket (the "confirm
    // closure" capability), and this coarse gate must not block a caller
    // the Service layer already supports. The Service layer still enforces
    // exactly which transition each role may request.
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> changeStatus(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long id,
            @Valid @RequestBody ChangeTicketStatusRequest request) {
        TicketDetailResponse updated = ticketService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.UPDATED, updated));
    }

    @Operation(summary = "Reopen a RESOLVED ticket",
            description = "Moves the ticket back to ASSIGNED with its existing engineer preserved. "
                    + "The ticket's creator may reopen only within the 7-day window; an administrator may reopen at any time.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ticket reopened")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is a SUPPORT_ENGINEER")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket not found or not visible to the caller")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Ticket isn't RESOLVED, or the reopen window has passed")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping("/{id}/reopen")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> reopenTicket(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long id,
            @Valid @RequestBody ReopenTicketRequest request) {
        TicketDetailResponse updated = ticketService.reopenTicket(id, request);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.UPDATED, updated));
    }

    @Operation(summary = "Soft-delete a ticket",
            description = "Administrator only. Never a hard delete - the ticket becomes invisible to all normal views.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ticket deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an administrator")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket not found")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTicket(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.DELETED));
    }
}
