package com.helpdesk.ticket.service.impl;

import com.helpdesk.exception.BadRequestException;
import com.helpdesk.exception.ConflictException;
import com.helpdesk.exception.ForbiddenException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.role.entity.Role;
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
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketSequence;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.mapper.TicketMapper;
import com.helpdesk.ticket.repository.CategoryRepository;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.repository.TicketSequenceRepository;
import com.helpdesk.ticket.service.CategoryService;
import com.helpdesk.tickethistory.entity.TicketHistory;
import com.helpdesk.tickethistory.entity.TicketHistoryAction;
import com.helpdesk.tickethistory.repository.TicketHistoryRepository;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — every collaborator is mocked, matching the rest of this
 * codebase's {@code *ServiceImplTest} convention; no Spring context, no
 * database. {@code SecurityContextHolder} is populated manually via
 * {@link #authenticateAs(Long, RoleName)} and cleared before/after every
 * test.
 * <p>
 * {@code TicketMapper}/{@code CategoryService} are mocked, so
 * {@code ticketMapper.updateEntity(ticket, request)} does nothing to
 * {@code ticket} by default (a mocked void method); tests that need
 * {@code updateTicket}'s "did the field actually change" branching to fire
 * correctly stub it with {@code doAnswer} to apply the request the same way
 * the real {@code TicketMapperImpl} does - a faithful simulation, not a
 * shortcut.
 * <p>
 * "Hidden after delete" ({@code deleteTicket}) can only be demonstrated at
 * this level by re-stubbing {@code ticketRepository.findById} to return
 * empty after the delete call - simulating what {@code Ticket}'s
 * {@code @SQLRestriction} does in a real database. The annotation itself is
 * a Hibernate/database-level guarantee no mocked-repository unit test can
 * exercise directly; that guarantee belongs to an integration test.
 */
class TicketServiceImplTest {

    private TicketRepository ticketRepository;
    private CategoryRepository categoryRepository;
    private TicketSequenceRepository ticketSequenceRepository;
    private TicketHistoryRepository ticketHistoryRepository;
    private UserRepository userRepository;
    private CategoryService categoryService;
    private TicketMapper ticketMapper;
    private TicketServiceImpl ticketService;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        ticketSequenceRepository = mock(TicketSequenceRepository.class);
        ticketHistoryRepository = mock(TicketHistoryRepository.class);
        userRepository = mock(UserRepository.class);
        categoryService = mock(CategoryService.class);
        ticketMapper = mock(TicketMapper.class);
        ticketService = new TicketServiceImpl(ticketRepository, categoryRepository, ticketSequenceRepository,
                ticketHistoryRepository, userRepository, categoryService, ticketMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // createTicket
    // ============================================================

    @Test
    void createTicket_shouldReturnMappedResponse_whenSuccessful() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Category category = anActiveCategory();
        CreateTicketRequest request = aCreateRequest(5L);
        Ticket ticket = new Ticket("HD-placeholder", "Title", "Description", category, TicketPriority.MEDIUM, creator);
        ReflectionTestUtils.setField(ticket, "id", 100L);
        TicketDetailResponse expected = aDetailResponse(100L);
        int year = Year.now().getValue();
        TicketSequence sequence = new TicketSequence(year);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketSequenceRepository.findByYear(year)).thenReturn(Optional.of(sequence));
        when(ticketMapper.toEntity(eq(request), any(String.class), eq(category), eq(creator))).thenReturn(ticket);
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(expected);

        TicketDetailResponse result = ticketService.createTicket(request);

        assertEquals(expected, result);
        verify(categoryService).validateActiveCategory(5L);
        verify(ticketRepository).save(ticket);
        verify(ticketMapper).toEntity(eq(request), any(String.class), eq(category), eq(creator));
    }

    @Test
    void createTicket_shouldThrowBadRequest_whenCategoryIsInactive() {
        authenticateAs(1L, RoleName.USER);
        CreateTicketRequest request = aCreateRequest(5L);
        doThrow(new BadRequestException("Category is not active: 'Retired'")).when(categoryService).validateActiveCategory(5L);

        assertThrows(BadRequestException.class, () -> ticketService.createTicket(request));

        verifyNoInteractions(categoryRepository, ticketRepository, ticketSequenceRepository, ticketMapper);
    }

    @Test
    void createTicket_shouldThrowNotFound_whenCategoryDoesNotExist() {
        authenticateAs(1L, RoleName.USER);
        CreateTicketRequest request = aCreateRequest(404L);
        doThrow(new ResourceNotFoundException("Category", "id", 404L)).when(categoryService).validateActiveCategory(404L);

        assertThrows(ResourceNotFoundException.class, () -> ticketService.createTicket(request));

        verifyNoInteractions(categoryRepository, ticketRepository, ticketSequenceRepository, ticketMapper);
    }

    @Test
    void createTicket_shouldAssignAuthenticatedUserAsCreator() {
        authenticateAs(7L, RoleName.USER);
        User creator = aUser(7L, RoleName.USER);
        Category category = anActiveCategory();
        CreateTicketRequest request = aCreateRequest(5L);
        Ticket ticket = new Ticket("HD-placeholder", "Title", "Description", category, TicketPriority.MEDIUM, creator);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(userRepository.findById(7L)).thenReturn(Optional.of(creator));
        stubSequence(Year.now().getValue(), 0L);
        when(ticketMapper.toEntity(any(), any(), any(), any())).thenReturn(ticket);
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(1L));

        ticketService.createTicket(request);

        verify(ticketMapper).toEntity(eq(request), any(String.class), eq(category), eq(creator));
    }

    @Test
    void createTicket_shouldLeaveInitialStatusOpenAndUnassigned() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Category category = anActiveCategory();
        CreateTicketRequest request = aCreateRequest(5L);
        Ticket ticket = new Ticket("HD-placeholder", "Title", "Description", category, TicketPriority.MEDIUM, creator);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        stubSequence(Year.now().getValue(), 0L);
        when(ticketMapper.toEntity(any(), any(), any(), any())).thenReturn(ticket);
        when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketMapper.toDetailResponse(any())).thenReturn(aDetailResponse(1L));

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertEquals(TicketStatus.OPEN, captor.getValue().getStatus());
        assertNull(captor.getValue().getAssignedTo());
    }

    // ============================================================
    // ticket number generation (via createTicket)
    // ============================================================

    @Test
    void generateTicketNumber_shouldIncrementExistingSequence_andFormatWithZeroPadding() {
        authenticateAs(1L, RoleName.USER);
        int year = Year.now().getValue();
        User creator = aUser(1L, RoleName.USER);
        Category category = anActiveCategory();
        CreateTicketRequest request = aCreateRequest(5L);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        stubSequence(year, 5L);
        when(ticketMapper.toEntity(any(), any(), any(), any()))
                .thenAnswer(invocation -> new Ticket(invocation.getArgument(1), "Title", "Description", category, TicketPriority.MEDIUM, creator));
        when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketMapper.toDetailResponse(any())).thenReturn(aDetailResponse(1L));

        ticketService.createTicket(request);

        String expectedNumber = "HD-%d-000006".formatted(year);
        verify(ticketMapper).toEntity(eq(request), eq(expectedNumber), eq(category), eq(creator));
    }

    @Test
    void generateTicketNumber_shouldBootstrapSequence_whenFirstTicketOfYear() {
        authenticateAs(1L, RoleName.USER);
        int year = Year.now().getValue();
        User creator = aUser(1L, RoleName.USER);
        Category category = anActiveCategory();
        CreateTicketRequest request = aCreateRequest(5L);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketSequenceRepository.findByYear(year)).thenReturn(Optional.empty());
        when(ticketSequenceRepository.save(any(TicketSequence.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketMapper.toEntity(any(), any(), any(), any()))
                .thenAnswer(invocation -> new Ticket(invocation.getArgument(1), "Title", "Description", category, TicketPriority.MEDIUM, creator));
        when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketMapper.toDetailResponse(any())).thenReturn(aDetailResponse(1L));

        ticketService.createTicket(request);

        String expectedNumber = "HD-%d-000001".formatted(year);
        verify(ticketMapper).toEntity(eq(request), eq(expectedNumber), eq(category), eq(creator));
        ArgumentCaptor<TicketSequence> captor = ArgumentCaptor.forClass(TicketSequence.class);
        verify(ticketSequenceRepository, times(2)).save(captor.capture());
        assertEquals(year, captor.getValue().getYear());
    }

    @Test
    void generateTicketNumber_shouldIncrementAcrossConsecutiveCreations() {
        authenticateAs(1L, RoleName.USER);
        int year = Year.now().getValue();
        User creator = aUser(1L, RoleName.USER);
        Category category = anActiveCategory();
        CreateTicketRequest request = aCreateRequest(5L);
        TicketSequence sequence = new TicketSequence(year);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketSequenceRepository.findByYear(year)).thenReturn(Optional.of(sequence));
        when(ticketMapper.toEntity(any(), any(), any(), any()))
                .thenAnswer(invocation -> new Ticket(invocation.getArgument(1), "Title", "Description", category, TicketPriority.MEDIUM, creator));
        when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketMapper.toDetailResponse(any())).thenReturn(aDetailResponse(1L));

        ticketService.createTicket(request);
        ticketService.createTicket(request);

        verify(ticketMapper).toEntity(eq(request), eq("HD-%d-000001".formatted(year)), eq(category), eq(creator));
        verify(ticketMapper).toEntity(eq(request), eq("HD-%d-000002".formatted(year)), eq(category), eq(creator));
    }

    // ============================================================
    // getTicketById
    // ============================================================

    @Test
    void getTicketById_shouldReturnTicket_whenUserIsCreator() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        TicketDetailResponse expected = aDetailResponse(10L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(expected);

        assertEquals(expected, ticketService.getTicketById(10L));
    }

    @Test
    void getTicketById_shouldThrowNotFound_whenUserIsNotCreator() {
        authenticateAs(2L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ResourceNotFoundException.class, () -> ticketService.getTicketById(10L));

        verifyNoInteractions(ticketMapper);
    }

    @Test
    void getTicketById_shouldReturnTicket_whenSupportEngineerIsAssigned() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, engineer);
        TicketDetailResponse expected = aDetailResponse(10L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(expected);

        assertEquals(expected, ticketService.getTicketById(10L));
    }

    @Test
    void getTicketById_shouldThrowNotFound_whenTicketUnassigned_forSupportEngineer() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ResourceNotFoundException.class, () -> ticketService.getTicketById(10L));

        verifyNoInteractions(ticketMapper);
    }

    @Test
    void getTicketById_shouldThrowNotFound_whenAssignedToDifferentEngineer() {
        authenticateAs(3L, RoleName.SUPPORT_ENGINEER);
        User creator = aUser(1L, RoleName.USER);
        User otherEngineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, otherEngineer);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ResourceNotFoundException.class, () -> ticketService.getTicketById(10L));

        verifyNoInteractions(ticketMapper);
    }

    @Test
    void getTicketById_shouldReturnTicket_whenAdminRegardlessOfOwnership() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        TicketDetailResponse expected = aDetailResponse(10L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(expected);

        assertEquals(expected, ticketService.getTicketById(10L));
    }

    @Test
    void getTicketById_shouldThrowNotFound_whenTicketDoesNotExist() {
        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ticketService.getTicketById(404L));

        verifyNoInteractions(ticketMapper);
    }

    // ============================================================
    // validateTicketAccess
    // ============================================================

    @Test
    void validateTicketAccess_shouldNotThrow_whenVisibleToCaller() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        ticketService.validateTicketAccess(10L);
    }

    @Test
    void validateTicketAccess_shouldThrowNotFound_whenNotVisibleToCaller() {
        authenticateAs(2L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ResourceNotFoundException.class, () -> ticketService.validateTicketAccess(10L));
    }

    // ============================================================
    // getTickets
    // ============================================================

    @Test
    void getTickets_shouldQueryByCreatedBy_whenCallerIsUser() {
        authenticateAs(1L, RoleName.USER);
        Pageable pageable = PageRequest.of(0, 20);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        TicketSummaryResponse summary = aSummaryResponse(10L);
        Page<Ticket> page = new PageImpl<>(List.of(ticket), pageable, 1);
        when(ticketRepository.findByCreatedById(1L, pageable)).thenReturn(page);
        when(ticketMapper.toSummaryResponse(ticket)).thenReturn(summary);

        Page<TicketSummaryResponse> result = ticketService.getTickets(pageable);

        assertEquals(List.of(summary), result.getContent());
        verify(ticketRepository).findByCreatedById(1L, pageable);
        verify(ticketRepository, never()).findByAssignedToId(any(), any());
        verify(ticketRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getTickets_shouldQueryByAssignedTo_whenCallerIsSupportEngineer() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        Pageable pageable = PageRequest.of(0, 20);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, engineer);
        Page<Ticket> page = new PageImpl<>(List.of(ticket), pageable, 1);
        when(ticketRepository.findByAssignedToId(2L, pageable)).thenReturn(page);
        when(ticketMapper.toSummaryResponse(ticket)).thenReturn(aSummaryResponse(10L));

        Page<TicketSummaryResponse> result = ticketService.getTickets(pageable);

        assertEquals(1, result.getTotalElements());
        verify(ticketRepository).findByAssignedToId(2L, pageable);
        verify(ticketRepository, never()).findByCreatedById(any(), any());
    }

    @Test
    void getTickets_shouldQueryAll_whenCallerIsAdmin() {
        authenticateAs(99L, RoleName.ADMIN);
        Pageable pageable = PageRequest.of(0, 20);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        Page<Ticket> page = new PageImpl<>(List.of(ticket), pageable, 1);
        when(ticketRepository.findAll(pageable)).thenReturn(page);
        when(ticketMapper.toSummaryResponse(ticket)).thenReturn(aSummaryResponse(10L));

        Page<TicketSummaryResponse> result = ticketService.getTickets(pageable);

        assertEquals(1, result.getTotalElements());
        verify(ticketRepository).findAll(pageable);
    }

    @Test
    void getTickets_shouldThrowBadRequest_whenSortFieldNotAllowed() {
        authenticateAs(1L, RoleName.USER);
        Pageable pageable = PageRequest.of(0, 20, Sort.by("passwordHash"));

        assertThrows(BadRequestException.class, () -> ticketService.getTickets(pageable));

        verifyNoInteractions(ticketRepository, ticketMapper);
    }

    // ============================================================
    // updateTicket
    // ============================================================

    @Test
    void updateTicket_shouldRecordTitleUpdated_whenOnlyTitleChanges() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        UpdateTicketRequest request = new UpdateTicketRequest("New Title", ticket.getDescription(), 0L);
        stubMapperToApplyUpdate(ticket, request);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        ticketService.updateTicket(10L, request);

        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(ticketHistoryRepository, times(1)).save(captor.capture());
        assertEquals(TicketHistoryAction.TITLE_UPDATED, captor.getValue().getAction());
    }

    @Test
    void updateTicket_shouldRecordDescriptionUpdated_whenOnlyDescriptionChanges() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        UpdateTicketRequest request = new UpdateTicketRequest(ticket.getTitle(), "New Description", 0L);
        stubMapperToApplyUpdate(ticket, request);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        ticketService.updateTicket(10L, request);

        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(ticketHistoryRepository, times(1)).save(captor.capture());
        assertEquals(TicketHistoryAction.DESCRIPTION_UPDATED, captor.getValue().getAction());
    }

    @Test
    void updateTicket_shouldRecordBothFieldChanges_whenBothChange() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        UpdateTicketRequest request = new UpdateTicketRequest("New Title", "New Description", 0L);
        stubMapperToApplyUpdate(ticket, request);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        ticketService.updateTicket(10L, request);

        verify(ticketHistoryRepository, times(2)).save(any());
    }

    @Test
    void updateTicket_shouldRecordNoHistory_whenNothingChanges() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        UpdateTicketRequest request = new UpdateTicketRequest(ticket.getTitle(), ticket.getDescription(), 0L);
        stubMapperToApplyUpdate(ticket, request);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        ticketService.updateTicket(10L, request);

        verifyNoInteractions(ticketHistoryRepository);
    }

    @Test
    void updateTicket_shouldThrowConflict_whenVersionStale() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        UpdateTicketRequest request = new UpdateTicketRequest("New Title", "New Description", 99L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.updateTicket(10L, request));

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(ticketHistoryRepository);
    }

    @Test
    void updateTicket_shouldThrowForbidden_whenCallerIsSupportEngineer() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, engineer);
        UpdateTicketRequest request = new UpdateTicketRequest("New Title", "New Description", 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> ticketService.updateTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateTicket_shouldThrowConflict_whenUserTicketNotInEditableStatus() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, null);
        UpdateTicketRequest request = new UpdateTicketRequest("New Title", "New Description", 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.updateTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateTicket_shouldThrowNotFound_whenTicketNotVisibleToCaller() {
        authenticateAs(2L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        UpdateTicketRequest request = new UpdateTicketRequest("New Title", "New Description", 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ResourceNotFoundException.class, () -> ticketService.updateTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateTicket_shouldAllowAdmin_regardlessOfStatus() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User admin = aUser(99L, RoleName.ADMIN);
        Ticket ticket = aTicket(10L, TicketStatus.CLOSED, creator, null);
        UpdateTicketRequest request = new UpdateTicketRequest("New Title", "New Description", 0L);
        stubMapperToApplyUpdate(ticket, request);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        assertNotNull(ticketService.updateTicket(10L, request));

        verify(ticketRepository).saveAndFlush(ticket);
    }

    // ============================================================
    // assignTicket
    // ============================================================

    @Test
    void assignTicket_shouldAssignEngineerAndSetStatusAssigned_whenTicketOpen() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        User admin = aUser(99L, RoleName.ADMIN);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        AssignTicketRequest request = new AssignTicketRequest(2L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(engineer));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        ticketService.assignTicket(10L, request);

        assertEquals(TicketStatus.ASSIGNED, ticket.getStatus());
        assertSame(engineer, ticket.getAssignedTo());
        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(ticketHistoryRepository).save(captor.capture());
        assertEquals(TicketHistoryAction.ASSIGNED, captor.getValue().getAction());
        assertEquals(ticket, captor.getValue().getTicket());
        assertEquals(admin, captor.getValue().getActor());
    }

    @Test
    void assignTicket_shouldThrowForbidden_whenCallerIsNotAdmin() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        AssignTicketRequest request = new AssignTicketRequest(2L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> ticketService.assignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void assignTicket_shouldThrowBadRequest_whenAgentIsNotSupportEngineer() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User notAnEngineer = aUser(3L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        AssignTicketRequest request = new AssignTicketRequest(3L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(3L)).thenReturn(Optional.of(notAnEngineer));

        assertThrows(BadRequestException.class, () -> ticketService.assignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void assignTicket_shouldThrowConflict_whenTicketAlreadyAssigned() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, engineer);
        AssignTicketRequest request = new AssignTicketRequest(2L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.assignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void assignTicket_shouldThrowConflict_whenTicketStatusIsInProgress() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.IN_PROGRESS, creator, engineer);
        AssignTicketRequest request = new AssignTicketRequest(2L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.assignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void assignTicket_shouldThrowConflict_whenVersionStale() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        AssignTicketRequest request = new AssignTicketRequest(2L, 99L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.assignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void assignTicket_shouldThrowNotFound_whenAgentDoesNotExist() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        AssignTicketRequest request = new AssignTicketRequest(404L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ticketService.assignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    // ============================================================
    // reassignTicket
    // ============================================================

    @Test
    void reassignTicket_shouldSucceed_whileAssigned() {
        reassignTicketSucceedsFromStatus(TicketStatus.ASSIGNED);
    }

    @Test
    void reassignTicket_shouldSucceed_whileInProgress() {
        reassignTicketSucceedsFromStatus(TicketStatus.IN_PROGRESS);
    }

    @Test
    void reassignTicket_shouldSucceed_whileWaitingForCustomer() {
        reassignTicketSucceedsFromStatus(TicketStatus.WAITING_FOR_CUSTOMER);
    }

    private void reassignTicketSucceedsFromStatus(TicketStatus startingStatus) {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User oldEngineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        User newEngineer = aUser(3L, RoleName.SUPPORT_ENGINEER);
        User admin = aUser(99L, RoleName.ADMIN);
        Ticket ticket = aTicket(10L, startingStatus, creator, oldEngineer);
        AssignTicketRequest request = new AssignTicketRequest(3L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(3L)).thenReturn(Optional.of(newEngineer));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        ticketService.reassignTicket(10L, request);

        assertEquals(TicketStatus.ASSIGNED, ticket.getStatus());
        assertSame(newEngineer, ticket.getAssignedTo());
        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(ticketHistoryRepository).save(captor.capture());
        assertEquals(TicketHistoryAction.REASSIGNED, captor.getValue().getAction());
        assertEquals("Test User 2", captor.getValue().getOldValue());
        assertEquals("Test User 3", captor.getValue().getNewValue());
    }

    @Test
    void reassignTicket_shouldThrowConflict_whenResolved() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, engineer);
        AssignTicketRequest request = new AssignTicketRequest(3L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.reassignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void reassignTicket_shouldThrowConflict_whenClosed() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.CLOSED, creator, engineer);
        AssignTicketRequest request = new AssignTicketRequest(3L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.reassignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void reassignTicket_shouldThrowBadRequest_whenAgentIsNotSupportEngineer() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User oldEngineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        User notAnEngineer = aUser(4L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, oldEngineer);
        AssignTicketRequest request = new AssignTicketRequest(4L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(4L)).thenReturn(Optional.of(notAnEngineer));

        assertThrows(BadRequestException.class, () -> ticketService.reassignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void reassignTicket_shouldThrowConflict_whenVersionStale() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User oldEngineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, oldEngineer);
        AssignTicketRequest request = new AssignTicketRequest(3L, 99L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.reassignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void reassignTicket_shouldThrowForbidden_whenCallerIsNotAdmin() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        User creator = aUser(1L, RoleName.USER);
        User oldEngineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, oldEngineer);
        AssignTicketRequest request = new AssignTicketRequest(3L, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> ticketService.reassignTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    // ============================================================
    // changeStatus - legal transitions
    // ============================================================

    @Test
    void changeStatus_shouldTransition_assignedToInProgress() {
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, engineer);
        assertLegalTransition(ticket, engineer, RoleName.SUPPORT_ENGINEER, TicketStatus.IN_PROGRESS);
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
    }

    @Test
    void changeStatus_shouldTransition_inProgressToWaitingForCustomer() {
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.IN_PROGRESS, creator, engineer);
        assertLegalTransition(ticket, engineer, RoleName.SUPPORT_ENGINEER, TicketStatus.WAITING_FOR_CUSTOMER);
        assertEquals(TicketStatus.WAITING_FOR_CUSTOMER, ticket.getStatus());
    }

    @Test
    void changeStatus_shouldTransition_waitingForCustomerToInProgress() {
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.WAITING_FOR_CUSTOMER, creator, engineer);
        assertLegalTransition(ticket, engineer, RoleName.SUPPORT_ENGINEER, TicketStatus.IN_PROGRESS);
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
    }

    @Test
    void changeStatus_shouldTransition_inProgressToResolved_andSetResolvedAt() {
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.IN_PROGRESS, creator, engineer);
        assertLegalTransition(ticket, engineer, RoleName.SUPPORT_ENGINEER, TicketStatus.RESOLVED);
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        assertNotNull(ticket.getResolvedAt());
    }

    @Test
    void changeStatus_shouldTransition_resolvedToClosed_andSetClosedAt() {
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, null);
        assertLegalTransition(ticket, creator, RoleName.USER, TicketStatus.CLOSED);
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertNotNull(ticket.getClosedAt());
    }

    /** Drives a single legal transition end-to-end as the given actor, verifying the STATUS_CHANGED history row. */
    private void assertLegalTransition(Ticket ticket, User actor, RoleName actorRole, TicketStatus target) {
        authenticateAs(actor.getId(), actorRole);
        TicketStatus oldStatus = ticket.getStatus();
        ChangeTicketStatusRequest request = new ChangeTicketStatusRequest(target, "moving along", 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        ticketService.changeStatus(10L, request);

        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(ticketHistoryRepository).save(captor.capture());
        assertEquals(TicketHistoryAction.STATUS_CHANGED, captor.getValue().getAction());
        assertEquals(oldStatus.name(), captor.getValue().getOldValue());
        assertEquals(target.name(), captor.getValue().getNewValue());
    }

    // ============================================================
    // changeStatus - illegal transitions
    // ============================================================

    @Test
    void changeStatus_shouldThrowConflict_whenOpenToClosedAttempted() {
        assertIllegalTransition(TicketStatus.OPEN, TicketStatus.CLOSED, RoleName.ADMIN);
    }

    @Test
    void changeStatus_shouldThrowConflict_whenOpenToResolvedAttempted() {
        assertIllegalTransition(TicketStatus.OPEN, TicketStatus.RESOLVED, RoleName.ADMIN);
    }

    @Test
    void changeStatus_shouldThrowConflict_whenWaitingForCustomerToClosedAttempted() {
        assertIllegalTransition(TicketStatus.WAITING_FOR_CUSTOMER, TicketStatus.CLOSED, RoleName.ADMIN);
    }

    @Test
    void changeStatus_shouldThrowConflict_whenClosedToInProgressAttempted() {
        assertIllegalTransition(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS, RoleName.ADMIN);
    }

    @Test
    void changeStatus_shouldThrowConflict_whenResolvedToAssignedAttempted_outsideReopenFlow() {
        assertIllegalTransition(TicketStatus.RESOLVED, TicketStatus.ASSIGNED, RoleName.ADMIN);
    }

    /** Even ADMIN cannot perform a transition absent from the workflow graph - proves the graph, not just the actor check, is enforced. */
    private void assertIllegalTransition(TicketStatus current, TicketStatus target, RoleName actorRole) {
        authenticateAs(99L, actorRole);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, current, creator, null);
        ChangeTicketStatusRequest request = new ChangeTicketStatusRequest(target, null, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.changeStatus(10L, request));

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(ticketHistoryRepository);
    }

    // ============================================================
    // changeStatus - actor permissions
    // ============================================================

    @Test
    void changeStatus_shouldThrowForbidden_whenUserAttemptsNonCloseTransition() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, null);
        ChangeTicketStatusRequest request = new ChangeTicketStatusRequest(TicketStatus.IN_PROGRESS, null, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> ticketService.changeStatus(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void changeStatus_shouldAllowUserToClose_asCreator() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, null);
        ChangeTicketStatusRequest request = new ChangeTicketStatusRequest(TicketStatus.CLOSED, "confirmed", 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        assertNotNull(ticketService.changeStatus(10L, request));

        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
    }

    @Test
    void changeStatus_shouldThrowForbidden_whenSupportEngineerAttemptsClose() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, engineer);
        ChangeTicketStatusRequest request = new ChangeTicketStatusRequest(TicketStatus.CLOSED, null, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> ticketService.changeStatus(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void changeStatus_shouldThrowNotFound_whenSupportEngineerNotAssigned() {
        authenticateAs(3L, RoleName.SUPPORT_ENGINEER);
        User creator = aUser(1L, RoleName.USER);
        User otherEngineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, otherEngineer);
        ChangeTicketStatusRequest request = new ChangeTicketStatusRequest(TicketStatus.IN_PROGRESS, null, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ResourceNotFoundException.class, () -> ticketService.changeStatus(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void changeStatus_shouldAllowAdmin_toCloseRegardlessOfCreator() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User admin = aUser(99L, RoleName.ADMIN);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, null);
        ChangeTicketStatusRequest request = new ChangeTicketStatusRequest(TicketStatus.CLOSED, null, 0L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        assertNotNull(ticketService.changeStatus(10L, request));

        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
    }

    @Test
    void changeStatus_shouldThrowConflict_whenVersionStale() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, engineer);
        ChangeTicketStatusRequest request = new ChangeTicketStatusRequest(TicketStatus.IN_PROGRESS, null, 99L);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.changeStatus(10L, request));

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(ticketHistoryRepository);
    }

    // ============================================================
    // reopenTicket
    // ============================================================

    @Test
    void reopenTicket_shouldMoveToAssigned_whenUserWithinWindow() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, engineer);
        ticket.setResolvedAt(Instant.now().minus(Duration.ofDays(2)));
        ReopenTicketRequest request = new ReopenTicketRequest("issue recurred");
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        ticketService.reopenTicket(10L, request);

        assertEquals(TicketStatus.ASSIGNED, ticket.getStatus());
        assertSame(engineer, ticket.getAssignedTo());
    }

    @Test
    void reopenTicket_shouldThrowConflict_whenUserWindowExpired() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, null);
        ticket.setResolvedAt(Instant.now().minus(Duration.ofDays(8)));
        ReopenTicketRequest request = new ReopenTicketRequest("too late");
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.reopenTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void reopenTicket_shouldMoveToAssigned_whenAdminRegardlessOfWindow() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        User admin = aUser(99L, RoleName.ADMIN);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, engineer);
        ticket.setResolvedAt(Instant.now().minus(Duration.ofDays(30)));
        ReopenTicketRequest request = new ReopenTicketRequest("admin override");
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        ticketService.reopenTicket(10L, request);

        assertEquals(TicketStatus.ASSIGNED, ticket.getStatus());
    }

    @Test
    void reopenTicket_shouldThrowForbidden_whenCallerIsSupportEngineer() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, engineer);
        ticket.setResolvedAt(Instant.now());
        ReopenTicketRequest request = new ReopenTicketRequest("attempt");
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> ticketService.reopenTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void reopenTicket_shouldThrowConflict_whenTicketNotResolved() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.IN_PROGRESS, creator, null);
        ReopenTicketRequest request = new ReopenTicketRequest("too soon");
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> ticketService.reopenTicket(10L, request));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void reopenTicket_shouldPreserveAssignedEngineer_andRecordReopenedHistory() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.RESOLVED, creator, engineer);
        ticket.setResolvedAt(Instant.now().minus(Duration.ofHours(1)));
        ReopenTicketRequest request = new ReopenTicketRequest("issue recurred");
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);
        when(ticketMapper.toDetailResponse(ticket)).thenReturn(aDetailResponse(10L));

        ticketService.reopenTicket(10L, request);

        assertSame(engineer, ticket.getAssignedTo());
        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(ticketHistoryRepository).save(captor.capture());
        assertEquals(TicketHistoryAction.REOPENED, captor.getValue().getAction());
        assertEquals(TicketStatus.RESOLVED.name(), captor.getValue().getOldValue());
        assertEquals(TicketStatus.ASSIGNED.name(), captor.getValue().getNewValue());
        assertEquals("issue recurred", captor.getValue().getNote());
    }

    // ============================================================
    // deleteTicket
    // ============================================================

    @Test
    void deleteTicket_shouldSoftDelete_whenCallerIsAdmin() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User admin = aUser(99L, RoleName.ADMIN);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        ticketService.deleteTicket(10L);

        assertNotNull(ticket.getDeletedAt());
        assertEquals(admin, ticket.getDeletedBy());
    }

    @Test
    void deleteTicket_shouldThrowForbidden_whenCallerIsUser() {
        authenticateAs(1L, RoleName.USER);
        User creator = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> ticketService.deleteTicket(10L));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void deleteTicket_shouldThrowForbidden_whenCallerIsSupportEngineer() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        User creator = aUser(1L, RoleName.USER);
        User engineer = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, engineer);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> ticketService.deleteTicket(10L));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void deleteTicket_shouldRecordSoftDeletedHistory() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User admin = aUser(99L, RoleName.ADMIN);
        Ticket ticket = aTicket(10L, TicketStatus.ASSIGNED, creator, null);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        ticketService.deleteTicket(10L);

        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(ticketHistoryRepository).save(captor.capture());
        assertEquals(TicketHistoryAction.SOFT_DELETED, captor.getValue().getAction());
        assertEquals(TicketStatus.ASSIGNED.name(), captor.getValue().getOldValue());
        assertEquals(admin, captor.getValue().getActor());
    }

    /**
     * {@code @SQLRestriction} itself is a Hibernate/database-level guarantee
     * this mocked-repository test cannot exercise directly - it simulates the
     * effect by re-stubbing {@code findById} to return empty after deletion,
     * the same outcome a real database would produce.
     */
    @Test
    void deleteTicket_thenGetTicketById_shouldThrowNotFound_whenRepositoryNoLongerReturnsTicket() {
        authenticateAs(99L, RoleName.ADMIN);
        User creator = aUser(1L, RoleName.USER);
        User admin = aUser(99L, RoleName.ADMIN);
        Ticket ticket = aTicket(10L, TicketStatus.OPEN, creator, null);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        ticketService.deleteTicket(10L);

        when(ticketRepository.findById(10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> ticketService.getTicketById(10L));
    }

    // --- fixtures ---

    private void authenticateAs(Long userId, RoleName role) {
        UserPrincipal principal = new UserPrincipal(userId, "user" + userId + "@example.com", "hashed-password", role, UserStatus.ACTIVE, 0);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User aUser(Long id, RoleName roleName) {
        Role role = new Role(roleName, roleName.name());
        User user = new User("Test User " + id, "user" + id + "@example.com", "hashed-password", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Category anActiveCategory() {
        Category category = new Category("Software", "Software issues");
        ReflectionTestUtils.setField(category, "id", 5L);
        return category;
    }

    private Ticket aTicket(Long id, TicketStatus status, User createdBy, User assignedTo) {
        Category category = anActiveCategory();
        Ticket ticket = new Ticket("HD-2026-%06d".formatted(id), "Original Title", "Original Description", category, TicketPriority.MEDIUM, createdBy);
        ReflectionTestUtils.setField(ticket, "id", id);
        ReflectionTestUtils.setField(ticket, "version", 0L);
        ticket.setStatus(status);
        if (assignedTo != null) {
            ticket.setAssignedTo(assignedTo);
        }
        return ticket;
    }

    private CreateTicketRequest aCreateRequest(Long categoryId) {
        return new CreateTicketRequest("Title", "Description", categoryId, TicketPriority.MEDIUM);
    }

    /** Makes the mocked {@code ticketMapper.updateEntity} apply the request the same way the real {@code TicketMapperImpl} does. */
    private void stubMapperToApplyUpdate(Ticket ticket, UpdateTicketRequest request) {
        doAnswer(invocation -> {
            ticket.setTitle(request.title());
            ticket.setDescription(request.description());
            return null;
        }).when(ticketMapper).updateEntity(ticket, request);
    }

    private void stubSequence(int year, long startingNextValue) {
        TicketSequence sequence = new TicketSequence(year);
        ReflectionTestUtils.setField(sequence, "nextValue", startingNextValue);
        when(ticketSequenceRepository.findByYear(year)).thenReturn(Optional.of(sequence));
    }

    private TicketDetailResponse aDetailResponse(Long id) {
        Instant now = Instant.now();
        return new TicketDetailResponse(id, "HD-2026-%06d".formatted(id), "Title", "Description",
                TicketStatus.OPEN, TicketPriority.MEDIUM, "Software", "Creator", null, null, null, 0L, now, now);
    }

    private TicketSummaryResponse aSummaryResponse(Long id) {
        Instant now = Instant.now();
        return new TicketSummaryResponse(id, "HD-2026-%06d".formatted(id), "Title",
                TicketStatus.OPEN, TicketPriority.MEDIUM, "Software", null, now, now);
    }
}
