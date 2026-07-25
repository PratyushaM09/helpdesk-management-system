package com.helpdesk.ticket.service.impl;

import com.helpdesk.exception.BadRequestException;
import com.helpdesk.exception.ConflictException;
import com.helpdesk.exception.ForbiddenException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.security.UserPrincipal;
import com.helpdesk.ticket.dto.request.AssignTicketRequest;
import com.helpdesk.ticket.dto.request.ChangeTicketStatusRequest;
import com.helpdesk.ticket.dto.request.CreateTicketRequest;
import com.helpdesk.ticket.dto.request.ReopenTicketRequest;
import com.helpdesk.ticket.dto.request.UpdateTicketRequest;
import com.helpdesk.ticket.dto.response.TicketDetailResponse;
import com.helpdesk.ticket.dto.response.TicketSummaryResponse;
import com.helpdesk.ticket.entity.Category;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketSequence;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.mapper.TicketMapper;
import com.helpdesk.ticket.repository.CategoryRepository;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.repository.TicketSequenceRepository;
import com.helpdesk.ticket.service.CategoryService;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.tickethistory.entity.TicketHistory;
import com.helpdesk.tickethistory.entity.TicketHistoryAction;
import com.helpdesk.tickethistory.repository.TicketHistoryRepository;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.repository.UserRepository;
import com.helpdesk.util.SortValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.Map;
import java.util.Set;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketServiceImpl.class);

    /** Public, sortable columns only - same "allow-listed sort fields" convention as {@code UserServiceImpl}. */
    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("id", "ticketNumber", "title", "status", "priority", "createdAt", "updatedAt");

    private static final String TICKET_NUMBER_PATTERN = "HD-%d-%06d";

    /** Default reopen window for a USER-initiated reopen (FR-TICK-7, Assumption A5) - not yet externalized to configuration. */
    private static final Duration USER_REOPEN_WINDOW = Duration.ofDays(7);

    /**
     * The only transitions {@link #changeStatus} may perform. {@code OPEN}
     * is deliberately absent as a key - the only way out of {@code OPEN} is
     * {@link #assignTicket}, never this generic method - and
     * {@code RESOLVED} maps only to {@code CLOSED} here; {@code RESOLVED ->
     * ASSIGNED} is the reopen event, performed only by {@link #reopenTicket}.
     * Applies identically to every caller, including ADMIN - "may not bypass
     * the workflow graph" means this map is never role-dependent.
     */
    private static final Map<TicketStatus, Set<TicketStatus>> LEGAL_STATUS_TRANSITIONS = Map.of(
            TicketStatus.ASSIGNED, Set.of(TicketStatus.IN_PROGRESS),
            TicketStatus.IN_PROGRESS, Set.of(TicketStatus.WAITING_FOR_CUSTOMER, TicketStatus.RESOLVED),
            TicketStatus.WAITING_FOR_CUSTOMER, Set.of(TicketStatus.IN_PROGRESS),
            TicketStatus.RESOLVED, Set.of(TicketStatus.CLOSED)
    );

    /** Statuses {@link #reassignTicket} may act on - already assigned and still active; {@code RESOLVED}/{@code CLOSED} must be reopened first. */
    private static final Set<TicketStatus> REASSIGNABLE_STATUSES =
            Set.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.WAITING_FOR_CUSTOMER);

    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;
    private final TicketSequenceRepository ticketSequenceRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final TicketMapper ticketMapper;

    public TicketServiceImpl(TicketRepository ticketRepository, CategoryRepository categoryRepository,
                              TicketSequenceRepository ticketSequenceRepository,
                              TicketHistoryRepository ticketHistoryRepository, UserRepository userRepository,
                              CategoryService categoryService, TicketMapper ticketMapper) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.ticketSequenceRepository = ticketSequenceRepository;
        this.ticketHistoryRepository = ticketHistoryRepository;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
        this.ticketMapper = ticketMapper;
    }

    @Override
    @Transactional
    public TicketDetailResponse createTicket(CreateTicketRequest request) {
        categoryService.validateActiveCategory(request.categoryId());
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.categoryId()));

        User createdBy = loadAuthenticatedUser();
        String ticketNumber = generateTicketNumber();

        Ticket ticket = ticketMapper.toEntity(request, ticketNumber, category, createdBy);
        Ticket saved = ticketRepository.save(ticket);

        log.info("Ticket created: ticketId={}, ticketNumber={}", saved.getId(), saved.getTicketNumber());
        return ticketMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDetailResponse getTicketById(Long id) {
        Ticket ticket = findTicketOrThrow(id);
        if (!isVisibleTo(ticket, currentPrincipal())) {
            throw new ResourceNotFoundException("Ticket", "id", id);
        }
        return ticketMapper.toDetailResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateTicketAccess(Long ticketId) {
        Ticket ticket = findTicketOrThrow(ticketId);
        if (!isVisibleTo(ticket, currentPrincipal())) {
            throw new ResourceNotFoundException("Ticket", "id", ticketId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketSummaryResponse> getTickets(Pageable pageable) {
        SortValidator.validate(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        UserPrincipal principal = currentPrincipal();
        Page<Ticket> tickets = switch (principal.role()) {
            case ADMIN -> ticketRepository.findAll(pageable);
            case USER -> ticketRepository.findByCreatedById(principal.userId(), pageable);
            case SUPPORT_ENGINEER -> ticketRepository.findByAssignedToId(principal.userId(), pageable);
        };
        return tickets.map(ticketMapper::toSummaryResponse);
    }

    @Override
    @Transactional
    public TicketDetailResponse assignTicket(Long ticketId, AssignTicketRequest request) {
        Ticket ticket = findTicketOrThrow(ticketId);
        UserPrincipal principal = currentPrincipal();
        requireAdmin(principal);
        validateVersion(ticket, request.version());

        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new ConflictException(
                    "Only an OPEN ticket can be assigned for the first time - current status: '%s'. Use reassignment instead."
                            .formatted(ticket.getStatus()));
        }

        User agent = resolveSupportEngineerOrThrow(request.agentId());
        ticket.setAssignedTo(agent);
        ticket.setStatus(TicketStatus.ASSIGNED);
        Ticket saved = ticketRepository.saveAndFlush(ticket);

        recordHistory(saved, loadAuthenticatedUser(), TicketHistoryAction.ASSIGNED, null, agent.getName(), null);

        log.info("Ticket assigned: ticketId={}, agentId={}", ticketId, agent.getId());
        return ticketMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional
    public TicketDetailResponse reassignTicket(Long ticketId, AssignTicketRequest request) {
        Ticket ticket = findTicketOrThrow(ticketId);
        UserPrincipal principal = currentPrincipal();
        requireAdmin(principal);
        validateVersion(ticket, request.version());

        if (!REASSIGNABLE_STATUSES.contains(ticket.getStatus())) {
            throw new ConflictException(
                    "Cannot reassign a ticket in status '%s' - reopen it first.".formatted(ticket.getStatus()));
        }

        User previousAgent = ticket.getAssignedTo();
        User newAgent = resolveSupportEngineerOrThrow(request.agentId());
        ticket.setAssignedTo(newAgent);
        ticket.setStatus(TicketStatus.ASSIGNED);
        Ticket saved = ticketRepository.saveAndFlush(ticket);

        String oldValue = previousAgent != null ? previousAgent.getName() : null;
        recordHistory(saved, loadAuthenticatedUser(), TicketHistoryAction.REASSIGNED, oldValue, newAgent.getName(), null);

        log.info("Ticket reassigned: ticketId={}, newAgentId={}", ticketId, newAgent.getId());
        return ticketMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional
    public TicketDetailResponse changeStatus(Long ticketId, ChangeTicketStatusRequest request) {
        Ticket ticket = findTicketOrThrow(ticketId);
        UserPrincipal principal = currentPrincipal();

        if (!isVisibleTo(ticket, principal)) {
            throw new ResourceNotFoundException("Ticket", "id", ticketId);
        }
        validateVersion(ticket, request.version());
        validateTransition(ticket.getStatus(), request.targetStatus());
        validateStatusChangeActor(request.targetStatus(), principal);

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(request.targetStatus());
        applyStatusSideEffects(ticket, request.targetStatus());
        Ticket saved = ticketRepository.saveAndFlush(ticket);

        recordHistory(saved, loadAuthenticatedUser(), TicketHistoryAction.STATUS_CHANGED,
                oldStatus.name(), request.targetStatus().name(), request.comment());

        log.info("Ticket status changed: ticketId={}, {} -> {}", ticketId, oldStatus, request.targetStatus());
        return ticketMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional
    public TicketDetailResponse reopenTicket(Long ticketId, ReopenTicketRequest request) {
        Ticket ticket = findTicketOrThrow(ticketId);
        UserPrincipal principal = currentPrincipal();

        if (!isVisibleTo(ticket, principal)) {
            throw new ResourceNotFoundException("Ticket", "id", ticketId);
        }
        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            throw new ConflictException(
                    "Only a RESOLVED ticket can be reopened - current status: '%s'.".formatted(ticket.getStatus()));
        }
        validateReopenActor(principal);
        validateReopenWindow(ticket, principal);

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.ASSIGNED);
        Ticket saved = ticketRepository.saveAndFlush(ticket);

        recordHistory(saved, loadAuthenticatedUser(), TicketHistoryAction.REOPENED,
                oldStatus.name(), TicketStatus.ASSIGNED.name(), request.reason());

        log.info("Ticket reopened: ticketId={}", ticketId);
        return ticketMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional
    public void deleteTicket(Long ticketId) {
        Ticket ticket = findTicketOrThrow(ticketId);
        requireAdmin(currentPrincipal());

        User admin = loadAuthenticatedUser();
        TicketStatus statusAtDeletion = ticket.getStatus();
        ticket.softDelete(admin, Instant.now());
        Ticket saved = ticketRepository.save(ticket);

        recordHistory(saved, admin, TicketHistoryAction.SOFT_DELETED, statusAtDeletion.name(), null, null);

        log.info("Ticket soft-deleted: ticketId={}, adminId={}", ticketId, admin.getId());
    }

    @Override
    @Transactional
    public TicketDetailResponse updateTicket(Long ticketId, UpdateTicketRequest request) {
        Ticket ticket = findTicketOrThrow(ticketId);
        UserPrincipal principal = currentPrincipal();

        if (!isVisibleTo(ticket, principal)) {
            throw new ResourceNotFoundException("Ticket", "id", ticketId);
        }
        validateVersion(ticket, request.version());
        validateTicketEditable(ticket, principal);

        String oldTitle = ticket.getTitle();
        String oldDescription = ticket.getDescription();

        ticketMapper.updateEntity(ticket, request);
        Ticket saved = ticketRepository.saveAndFlush(ticket);

        User actor = loadAuthenticatedUser();
        recordFieldChangeIfDifferent(saved, actor, TicketHistoryAction.TITLE_UPDATED, oldTitle, saved.getTitle());
        recordFieldChangeIfDifferent(saved, actor, TicketHistoryAction.DESCRIPTION_UPDATED, oldDescription, saved.getDescription());

        log.info("Ticket updated: ticketId={}", ticketId);
        return ticketMapper.toDetailResponse(saved);
    }

    /**
     * Bumps the per-year counter under {@code TicketSequenceRepository.findByYear}'s
     * pessimistic lock (held for the rest of this transaction) and formats
     * {@code HD-{year}-{sequence}} (FR-TICK-1), zero-padded to 6 digits.
     * <p>
     * Bootstrap edge case, accepted rather than defended against: the very
     * first ticket created in a new calendar year has a narrow window where
     * two concurrent callers could both find no row for that year and both
     * attempt to insert it - one succeeds, the other fails on the unique
     * primary key and that single request's transaction rolls back (a 500,
     * simply retried by the caller). Guarding against this would need a
     * {@code REQUIRES_NEW}-propagated retry step, disproportionate machinery
     * for a race confined to a few requests once per year at human-driven
     * (not machine-driven) ticket-creation rates.
     */
    private String generateTicketNumber() {
        int year = Year.now().getValue();
        TicketSequence sequence = ticketSequenceRepository.findByYear(year)
                .orElseGet(() -> ticketSequenceRepository.save(new TicketSequence(year)));

        long next = sequence.incrementAndGet();
        ticketSequenceRepository.save(sequence);

        return TICKET_NUMBER_PATTERN.formatted(year, next);
    }

    private boolean isVisibleTo(Ticket ticket, UserPrincipal principal) {
        return switch (principal.role()) {
            case ADMIN -> true;
            case USER -> ticket.getCreatedBy().getId().equals(principal.userId());
            case SUPPORT_ENGINEER -> ticket.getAssignedTo() != null
                    && ticket.getAssignedTo().getId().equals(principal.userId());
        };
    }

    private void validateTransition(TicketStatus current, TicketStatus target) {
        Set<TicketStatus> allowedNext = LEGAL_STATUS_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowedNext.contains(target)) {
            throw new ConflictException("Cannot transition ticket from '%s' to '%s'.".formatted(current, target));
        }
    }

    /**
     * Assumes {@link #isVisibleTo} already ran for this call and passed -
     * for a non-ADMIN caller that already means "USER is this ticket's
     * creator" or "SUPPORT_ENGINEER is this ticket's assigned engineer," so
     * this method only decides which *role* may attempt {@code targetStatus},
     * never re-checks ownership/assignment a second time.
     */
    private void validateStatusChangeActor(TicketStatus targetStatus, UserPrincipal principal) {
        if (principal.role() == RoleName.ADMIN) {
            return;
        }
        if (targetStatus == TicketStatus.CLOSED) {
            if (principal.role() == RoleName.USER) {
                return;
            }
            throw new ForbiddenException("Only the ticket's creator or an administrator may close this ticket.");
        }
        if (principal.role() == RoleName.SUPPORT_ENGINEER) {
            return;
        }
        throw new ForbiddenException("Only the assigned support engineer or an administrator may update this ticket's status.");
    }

    /** Same "already verified by {@code isVisibleTo}" assumption as {@link #validateStatusChangeActor}. */
    private void validateReopenActor(UserPrincipal principal) {
        if (principal.role() == RoleName.SUPPORT_ENGINEER) {
            throw new ForbiddenException("Support engineers cannot reopen tickets.");
        }
    }

    /**
     * Same "already verified by {@code isVisibleTo}" assumption as {@link #validateStatusChangeActor}
     * for the USER branch. SUPPORT_ENGINEER is rejected outright (FR-TICK-4
     * scopes editing to the creator or ADMIN, never the assigned engineer).
     */
    private void validateTicketEditable(Ticket ticket, UserPrincipal principal) {
        if (principal.role() == RoleName.ADMIN) {
            return;
        }
        if (principal.role() == RoleName.SUPPORT_ENGINEER) {
            throw new ForbiddenException("Support engineers cannot edit ticket title or description.");
        }
        if (ticket.getStatus() != TicketStatus.OPEN && ticket.getStatus() != TicketStatus.WAITING_FOR_CUSTOMER) {
            throw new ConflictException(
                    "Ticket can only be edited while OPEN or WAITING_FOR_CUSTOMER - current status: '%s'."
                            .formatted(ticket.getStatus()));
        }
    }

    /** Records a history row only if this specific field actually changed - a no-op edit writes nothing. */
    private void recordFieldChangeIfDifferent(Ticket ticket, User actor, TicketHistoryAction action, String oldValue, String newValue) {
        if (!oldValue.equals(newValue)) {
            recordHistory(ticket, actor, action, oldValue, newValue, null);
        }
    }

    private void validateReopenWindow(Ticket ticket, UserPrincipal principal) {
        if (principal.role() == RoleName.ADMIN) {
            return;
        }
        Instant resolvedAt = ticket.getResolvedAt();
        if (resolvedAt == null || resolvedAt.plus(USER_REOPEN_WINDOW).isBefore(Instant.now())) {
            throw new ConflictException(
                    "This ticket can no longer be reopened - the %d-day reopen window has passed."
                            .formatted(USER_REOPEN_WINDOW.toDays()));
        }
    }

    /** Sets {@code resolvedAt}/{@code closedAt} to "most recently entered this state" - overwritten on each re-entry, never cleared on exit. */
    private void applyStatusSideEffects(Ticket ticket, TicketStatus targetStatus) {
        Instant now = Instant.now();
        if (targetStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(now);
        } else if (targetStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(now);
        }
    }

    private void validateVersion(Ticket ticket, Long requestVersion) {
        if (!ticket.getVersion().equals(requestVersion)) {
            throw new ConflictException("This ticket was updated by someone else - please refresh and try again.");
        }
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal.role() != RoleName.ADMIN) {
            throw new ForbiddenException("Only an administrator may perform this action.");
        }
    }

    private User resolveSupportEngineerOrThrow(Long agentId) {
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", agentId));
        if (agent.getRole().getName() != RoleName.SUPPORT_ENGINEER) {
            throw new BadRequestException("User '%s' does not have the SUPPORT_ENGINEER role.".formatted(agent.getEmail()));
        }
        return agent;
    }

    private void recordHistory(Ticket ticket, User actor, TicketHistoryAction action,
                                String oldValue, String newValue, String note) {
        ticketHistoryRepository.save(new TicketHistory(ticket, actor, action, oldValue, newValue, note, false));
    }

    private Ticket findTicketOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", id));
    }

    /** Same "re-read the current row, never trust the JWT-issued snapshot" convention as {@code AccountServiceImpl.loadAuthenticatedUser}. */
    private User loadAuthenticatedUser() {
        UserPrincipal principal = currentPrincipal();
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.userId()));
    }

    private UserPrincipal currentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
