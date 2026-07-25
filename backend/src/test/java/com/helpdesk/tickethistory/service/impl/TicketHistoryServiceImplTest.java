package com.helpdesk.tickethistory.service.impl;

import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.security.UserPrincipal;
import com.helpdesk.ticket.entity.Category;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.tickethistory.dto.response.TicketHistoryResponse;
import com.helpdesk.tickethistory.entity.TicketHistory;
import com.helpdesk.tickethistory.entity.TicketHistoryAction;
import com.helpdesk.tickethistory.mapper.TicketHistoryMapper;
import com.helpdesk.tickethistory.repository.TicketHistoryRepository;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — every collaborator is mocked, matching {@code CommentServiceImplTest}'s
 * convention; no Spring context, no database. Read-only: no write/record
 * tests belong here (ADR-0006, TicketHistoryService's own Javadoc) - history
 * is written only by {@code TicketService}.
 */
class TicketHistoryServiceImplTest {

    private TicketHistoryRepository ticketHistoryRepository;
    private TicketService ticketService;
    private TicketHistoryMapper ticketHistoryMapper;
    private TicketHistoryServiceImpl ticketHistoryService;

    @BeforeEach
    void setUp() {
        ticketHistoryRepository = mock(TicketHistoryRepository.class);
        ticketService = mock(TicketService.class);
        ticketHistoryMapper = mock(TicketHistoryMapper.class);
        ticketHistoryService = new TicketHistoryServiceImpl(ticketHistoryRepository, ticketService, ticketHistoryMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getHistory_shouldReturnFilteredHistory_whenCallerIsUser() {
        authenticateAs(1L, RoleName.USER);
        Pageable pageable = PageRequest.of(0, 20);
        User actor = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, actor);
        TicketHistory history = aTicketHistory(500L, ticket, actor, TicketHistoryAction.STATUS_CHANGED);
        TicketHistoryResponse response = aHistoryResponse(500L, TicketHistoryAction.STATUS_CHANGED);
        Page<TicketHistory> page = new PageImpl<>(List.of(history), pageable, 1);
        when(ticketHistoryRepository.findByTicketIdAndInternalFalse(10L, pageable)).thenReturn(page);
        when(ticketHistoryMapper.toResponse(history)).thenReturn(response);

        Page<TicketHistoryResponse> result = ticketHistoryService.getHistory(10L, pageable);

        assertEquals(List.of(response), result.getContent());
        verify(ticketHistoryRepository).findByTicketIdAndInternalFalse(10L, pageable);
        verify(ticketHistoryRepository, never()).findByTicketId(any(), any());
    }

    @Test
    void getHistory_shouldReturnFullHistory_whenCallerIsSupportEngineer() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        Pageable pageable = PageRequest.of(0, 20);
        User actor = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, actor);
        TicketHistory internalEntry = aTicketHistory(501L, ticket, actor, TicketHistoryAction.ASSIGNED);
        TicketHistoryResponse response = aHistoryResponse(501L, TicketHistoryAction.ASSIGNED);
        Page<TicketHistory> page = new PageImpl<>(List.of(internalEntry), pageable, 1);
        when(ticketHistoryRepository.findByTicketId(10L, pageable)).thenReturn(page);
        when(ticketHistoryMapper.toResponse(internalEntry)).thenReturn(response);

        Page<TicketHistoryResponse> result = ticketHistoryService.getHistory(10L, pageable);

        assertEquals(List.of(response), result.getContent());
        verify(ticketHistoryRepository).findByTicketId(10L, pageable);
        verify(ticketHistoryRepository, never()).findByTicketIdAndInternalFalse(any(), any());
    }

    @Test
    void getHistory_shouldReturnFullHistory_whenCallerIsAdmin() {
        authenticateAs(99L, RoleName.ADMIN);
        Pageable pageable = PageRequest.of(0, 20);
        User actor = aUser(99L, RoleName.ADMIN);
        Ticket ticket = aTicket(10L, actor);
        TicketHistory entry = aTicketHistory(502L, ticket, actor, TicketHistoryAction.SOFT_DELETED);
        TicketHistoryResponse response = aHistoryResponse(502L, TicketHistoryAction.SOFT_DELETED);
        Page<TicketHistory> page = new PageImpl<>(List.of(entry), pageable, 1);
        when(ticketHistoryRepository.findByTicketId(10L, pageable)).thenReturn(page);
        when(ticketHistoryMapper.toResponse(entry)).thenReturn(response);

        Page<TicketHistoryResponse> result = ticketHistoryService.getHistory(10L, pageable);

        assertEquals(List.of(response), result.getContent());
        verify(ticketHistoryRepository).findByTicketId(10L, pageable);
    }

    @Test
    void getHistory_shouldReturnEmptyPage_whenNoHistoryExists() {
        authenticateAs(99L, RoleName.ADMIN);
        Pageable pageable = PageRequest.of(0, 20);
        Page<TicketHistory> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(ticketHistoryRepository.findByTicketId(10L, pageable)).thenReturn(emptyPage);

        Page<TicketHistoryResponse> result = ticketHistoryService.getHistory(10L, pageable);

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
        verifyNoInteractions(ticketHistoryMapper);
    }

    @Test
    void getHistory_shouldThrowNotFound_whenTicketNotVisibleToCaller() {
        authenticateAs(1L, RoleName.USER);
        Pageable pageable = PageRequest.of(0, 20);
        doThrow(new ResourceNotFoundException("Ticket", "id", 10L)).when(ticketService).validateTicketAccess(10L);

        assertThrows(ResourceNotFoundException.class, () -> ticketHistoryService.getHistory(10L, pageable));

        verifyNoInteractions(ticketHistoryRepository, ticketHistoryMapper);
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

    private Ticket aTicket(Long id, User createdBy) {
        Category category = new Category("Software", "Software issues");
        Ticket ticket = new Ticket("HD-2026-%06d".formatted(id), "Title", "Description", category, TicketPriority.MEDIUM, createdBy);
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }

    private TicketHistory aTicketHistory(Long id, Ticket ticket, User actor, TicketHistoryAction action) {
        TicketHistory history = new TicketHistory(ticket, actor, action, "OLD", "NEW", null, false);
        ReflectionTestUtils.setField(history, "id", id);
        return history;
    }

    private TicketHistoryResponse aHistoryResponse(Long id, TicketHistoryAction action) {
        return new TicketHistoryResponse(id, action, "OLD", "NEW", "Actor Name", null, Instant.now());
    }
}
