package com.helpdesk.comment.service.impl;

import com.helpdesk.comment.dto.request.CreateCommentRequest;
import com.helpdesk.comment.dto.request.UpdateCommentRequest;
import com.helpdesk.comment.dto.response.CommentResponse;
import com.helpdesk.comment.entity.Comment;
import com.helpdesk.comment.entity.CommentVisibility;
import com.helpdesk.comment.mapper.CommentMapper;
import com.helpdesk.comment.repository.CommentRepository;
import com.helpdesk.exception.ForbiddenException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.security.UserPrincipal;
import com.helpdesk.ticket.entity.Category;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — every collaborator is mocked, matching {@code AccountServiceImplTest}'s
 * convention; no Spring context, no database. {@code SecurityContextHolder} is
 * populated manually via {@link #authenticateAs(Long, RoleName)} (parameterized
 * by role, unlike {@code AccountServiceImplTest}'s fixed-USER helper, since
 * these tests exercise all three roles) and cleared before/after every test.
 * <p>
 * No "optimistic version mismatch" tests: {@code Comment} carries no
 * {@code @Version} column and {@code UpdateCommentRequest} carries no
 * {@code version} field (Service Milestone 3 design, confirmed in review) —
 * there is nothing to test here.
 * <p>
 * No "history recording" verification: {@code CommentServiceImpl} was never
 * asked to write {@code TicketHistory} rows in any milestone brief (only
 * {@code TicketService} writes history, per ADR-0006) — {@code COMMENT_ADDED}
 * remains a reserved-but-unused {@code TicketHistoryAction} value.
 */
class CommentServiceImplTest {

    private CommentRepository commentRepository;
    private TicketRepository ticketRepository;
    private UserRepository userRepository;
    private TicketService ticketService;
    private CommentMapper commentMapper;
    private CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        ticketRepository = mock(TicketRepository.class);
        userRepository = mock(UserRepository.class);
        ticketService = mock(TicketService.class);
        commentMapper = mock(CommentMapper.class);
        commentService = new CommentServiceImpl(commentRepository, ticketRepository, userRepository, ticketService, commentMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- addComment ---

    @Test
    void addComment_shouldCreatePublicComment_whenVisibilityExplicitlyPublic() {
        authenticateAs(1L, RoleName.USER);
        CreateCommentRequest request = new CreateCommentRequest("Please help", CommentVisibility.PUBLIC);
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(100L, ticket, author, CommentVisibility.PUBLIC);
        CommentResponse expected = aCommentResponse(100L, "Please help", CommentVisibility.PUBLIC);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(commentMapper.toEntity(request, ticket, author, CommentVisibility.PUBLIC)).thenReturn(comment);
        when(commentRepository.save(comment)).thenReturn(comment);
        when(commentMapper.toResponse(comment)).thenReturn(expected);

        CommentResponse result = commentService.addComment(10L, request);

        assertEquals(expected, result);
        verify(ticketService).validateTicketAccess(10L);
        verify(commentRepository).save(comment);
    }

    @Test
    void addComment_shouldDefaultToPublicVisibility_whenVisibilityIsNull() {
        authenticateAs(1L, RoleName.USER);
        CreateCommentRequest request = new CreateCommentRequest("Please help", null);
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(100L, ticket, author, CommentVisibility.PUBLIC);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(commentMapper.toEntity(request, ticket, author, CommentVisibility.PUBLIC)).thenReturn(comment);
        when(commentRepository.save(comment)).thenReturn(comment);
        when(commentMapper.toResponse(comment)).thenReturn(aCommentResponse(100L, "Please help", CommentVisibility.PUBLIC));

        commentService.addComment(10L, request);

        verify(commentMapper).toEntity(request, ticket, author, CommentVisibility.PUBLIC);
    }

    @Test
    void addComment_shouldAllowInternalComment_whenAuthorIsSupportEngineer() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        CreateCommentRequest request = new CreateCommentRequest("Internal note", CommentVisibility.INTERNAL);
        User author = aUser(2L, RoleName.SUPPORT_ENGINEER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(101L, ticket, author, CommentVisibility.INTERNAL);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(commentMapper.toEntity(request, ticket, author, CommentVisibility.INTERNAL)).thenReturn(comment);
        when(commentRepository.save(comment)).thenReturn(comment);
        when(commentMapper.toResponse(comment)).thenReturn(aCommentResponse(101L, "Internal note", CommentVisibility.INTERNAL));

        CommentResponse result = commentService.addComment(10L, request);

        assertEquals(CommentVisibility.INTERNAL, result.visibility());
        verify(commentMapper).toEntity(request, ticket, author, CommentVisibility.INTERNAL);
    }

    @Test
    void addComment_shouldAllowInternalComment_whenAuthorIsAdmin() {
        authenticateAs(99L, RoleName.ADMIN);
        CreateCommentRequest request = new CreateCommentRequest("Internal note", CommentVisibility.INTERNAL);
        User author = aUser(99L, RoleName.ADMIN);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(102L, ticket, author, CommentVisibility.INTERNAL);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.of(author));
        when(commentMapper.toEntity(request, ticket, author, CommentVisibility.INTERNAL)).thenReturn(comment);
        when(commentRepository.save(comment)).thenReturn(comment);
        when(commentMapper.toResponse(comment)).thenReturn(aCommentResponse(102L, "Internal note", CommentVisibility.INTERNAL));

        CommentResponse result = commentService.addComment(10L, request);

        assertEquals(CommentVisibility.INTERNAL, result.visibility());
        verify(commentMapper).toEntity(request, ticket, author, CommentVisibility.INTERNAL);
    }

    @Test
    void addComment_shouldThrowForbidden_whenUserRequestsInternalVisibility() {
        authenticateAs(1L, RoleName.USER);
        CreateCommentRequest request = new CreateCommentRequest("Sneaky", CommentVisibility.INTERNAL);
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () -> commentService.addComment(10L, request));

        verify(commentRepository, never()).save(any());
        verifyNoInteractions(commentMapper, userRepository);
    }

    @Test
    void addComment_shouldThrowNotFound_whenTicketNotVisibleToCaller() {
        authenticateAs(1L, RoleName.USER);
        CreateCommentRequest request = new CreateCommentRequest("Please help", null);
        doThrow(new ResourceNotFoundException("Ticket", "id", 10L)).when(ticketService).validateTicketAccess(10L);

        assertThrows(ResourceNotFoundException.class, () -> commentService.addComment(10L, request));

        verifyNoInteractions(ticketRepository, commentRepository, commentMapper, userRepository);
    }

    // --- updateComment ---

    @Test
    void updateComment_shouldUpdateContent_whenCallerIsAuthor() {
        authenticateAs(1L, RoleName.USER);
        UpdateCommentRequest request = new UpdateCommentRequest("Edited content");
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(100L, ticket, author, CommentVisibility.PUBLIC);
        CommentResponse expected = aCommentResponse(100L, "Edited content", CommentVisibility.PUBLIC);
        when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);
        when(commentMapper.toResponse(comment)).thenReturn(expected);

        CommentResponse result = commentService.updateComment(100L, request);

        assertEquals(expected, result);
        verify(commentMapper).updateEntity(eq(comment), eq(request), any(Instant.class));
        verify(commentRepository).save(comment);
    }

    @Test
    void updateComment_shouldUpdateContent_whenCallerIsAdmin() {
        authenticateAs(99L, RoleName.ADMIN);
        UpdateCommentRequest request = new UpdateCommentRequest("Edited by admin");
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(100L, ticket, author, CommentVisibility.PUBLIC);
        CommentResponse expected = aCommentResponse(100L, "Edited by admin", CommentVisibility.PUBLIC);
        when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);
        when(commentMapper.toResponse(comment)).thenReturn(expected);

        CommentResponse result = commentService.updateComment(100L, request);

        assertEquals(expected, result);
        verify(commentRepository).save(comment);
    }

    @Test
    void updateComment_shouldThrowForbidden_whenCallerIsNotAuthorOrAdmin() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        UpdateCommentRequest request = new UpdateCommentRequest("Hijack attempt");
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(100L, ticket, author, CommentVisibility.PUBLIC);
        when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));

        assertThrows(ForbiddenException.class, () -> commentService.updateComment(100L, request));

        verify(commentRepository, never()).save(any());
        verifyNoInteractions(commentMapper);
    }

    @Test
    void updateComment_shouldThrowNotFound_whenCommentDoesNotExist() {
        authenticateAs(1L, RoleName.USER);
        UpdateCommentRequest request = new UpdateCommentRequest("Edited");
        when(commentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> commentService.updateComment(404L, request));

        verify(commentRepository, never()).save(any());
        verifyNoInteractions(commentMapper);
    }

    // --- deleteComment ---

    @Test
    void deleteComment_shouldDelete_whenCallerIsAuthor() {
        authenticateAs(1L, RoleName.USER);
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(100L, ticket, author, CommentVisibility.PUBLIC);
        when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(100L);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_shouldDelete_whenCallerIsAdmin() {
        authenticateAs(99L, RoleName.ADMIN);
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(100L, ticket, author, CommentVisibility.PUBLIC);
        when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(100L);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_shouldThrowForbidden_whenCallerIsNotAuthorOrAdmin() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(100L, ticket, author, CommentVisibility.PUBLIC);
        when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));

        assertThrows(ForbiddenException.class, () -> commentService.deleteComment(100L));

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_shouldThrowNotFound_whenCommentDoesNotExist() {
        authenticateAs(1L, RoleName.USER);
        when(commentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> commentService.deleteComment(404L));

        verify(commentRepository, never()).delete(any());
    }

    // --- getComments ---

    @Test
    void getComments_shouldReturnOnlyPublicComments_whenCallerIsUser() {
        authenticateAs(1L, RoleName.USER);
        Pageable pageable = PageRequest.of(0, 20);
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment publicComment = aComment(100L, ticket, author, CommentVisibility.PUBLIC);
        CommentResponse response = aCommentResponse(100L, "Hi", CommentVisibility.PUBLIC);
        Page<Comment> page = new PageImpl<>(List.of(publicComment), pageable, 1);
        when(commentRepository.findByTicketIdAndVisibility(10L, CommentVisibility.PUBLIC, pageable)).thenReturn(page);
        when(commentMapper.toResponse(publicComment)).thenReturn(response);

        Page<CommentResponse> result = commentService.getComments(10L, pageable);

        assertEquals(List.of(response), result.getContent());
        verify(commentRepository).findByTicketIdAndVisibility(10L, CommentVisibility.PUBLIC, pageable);
        verify(commentRepository, never()).findByTicketId(any(), any());
    }

    @Test
    void getComments_shouldReturnAllComments_whenCallerIsSupportEngineer() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        Pageable pageable = PageRequest.of(0, 20);
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(100L, ticket, author, CommentVisibility.INTERNAL);
        Page<Comment> page = new PageImpl<>(List.of(comment), pageable, 1);
        when(commentRepository.findByTicketId(10L, pageable)).thenReturn(page);
        when(commentMapper.toResponse(comment)).thenReturn(aCommentResponse(100L, "Internal", CommentVisibility.INTERNAL));

        Page<CommentResponse> result = commentService.getComments(10L, pageable);

        assertEquals(1, result.getTotalElements());
        verify(commentRepository).findByTicketId(10L, pageable);
        verify(commentRepository, never()).findByTicketIdAndVisibility(any(), any(), any());
    }

    @Test
    void getComments_shouldReturnAllComments_whenCallerIsAdmin() {
        authenticateAs(99L, RoleName.ADMIN);
        Pageable pageable = PageRequest.of(0, 20);
        User author = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, author);
        Comment comment = aComment(100L, ticket, author, CommentVisibility.INTERNAL);
        Page<Comment> page = new PageImpl<>(List.of(comment), pageable, 1);
        when(commentRepository.findByTicketId(10L, pageable)).thenReturn(page);
        when(commentMapper.toResponse(comment)).thenReturn(aCommentResponse(100L, "Internal", CommentVisibility.INTERNAL));

        Page<CommentResponse> result = commentService.getComments(10L, pageable);

        assertEquals(1, result.getTotalElements());
        verify(commentRepository).findByTicketId(10L, pageable);
        verify(commentRepository, never()).findByTicketIdAndVisibility(any(), any(), any());
    }

    @Test
    void getComments_shouldThrowNotFound_whenTicketNotVisibleToCaller() {
        authenticateAs(1L, RoleName.USER);
        Pageable pageable = PageRequest.of(0, 20);
        doThrow(new ResourceNotFoundException("Ticket", "id", 10L)).when(ticketService).validateTicketAccess(10L);

        assertThrows(ResourceNotFoundException.class, () -> commentService.getComments(10L, pageable));

        verifyNoInteractions(commentRepository, commentMapper);
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

    private Comment aComment(Long id, Ticket ticket, User author, CommentVisibility visibility) {
        Comment comment = new Comment(ticket, author, "content", visibility);
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    private CommentResponse aCommentResponse(Long id, String content, CommentVisibility visibility) {
        return new CommentResponse(id, "Author Name", content, visibility, false, null, Instant.now());
    }
}
