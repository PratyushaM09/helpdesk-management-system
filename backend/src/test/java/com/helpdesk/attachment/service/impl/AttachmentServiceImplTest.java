package com.helpdesk.attachment.service.impl;

import com.helpdesk.attachment.dto.response.AttachmentResponse;
import com.helpdesk.attachment.entity.Attachment;
import com.helpdesk.attachment.mapper.AttachmentMapper;
import com.helpdesk.attachment.repository.AttachmentRepository;
import com.helpdesk.comment.entity.Comment;
import com.helpdesk.comment.entity.CommentVisibility;
import com.helpdesk.comment.repository.CommentRepository;
import com.helpdesk.exception.BadRequestException;
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
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — every collaborator is mocked, matching {@code CommentServiceImplTest}'s
 * convention; no Spring context, no database, no real file storage. No
 * {@code MultipartFile} appears anywhere: {@code AttachmentService.uploadAttachment}
 * takes already-extracted plain values (Service Milestone 3 design — the
 * {@code MultipartFile} only ever exists in {@code AttachmentController},
 * which is out of scope for this milestone) — nothing to mock there.
 * <p>
 * {@link ArgumentCaptor} is used for the upload tests specifically because
 * {@code Attachment} is constructed *inside* {@code uploadAttachment} itself
 * (no {@code AttachmentMapper.toEntity} exists) — the same convention
 * {@code AccountServiceImplTest} uses for token entities created inside
 * {@code forgotPassword}/{@code resendVerification}.
 */
class AttachmentServiceImplTest {

    private AttachmentRepository attachmentRepository;
    private TicketRepository ticketRepository;
    private CommentRepository commentRepository;
    private UserRepository userRepository;
    private TicketService ticketService;
    private AttachmentMapper attachmentMapper;
    private AttachmentServiceImpl attachmentService;

    @BeforeEach
    void setUp() {
        attachmentRepository = mock(AttachmentRepository.class);
        ticketRepository = mock(TicketRepository.class);
        commentRepository = mock(CommentRepository.class);
        userRepository = mock(UserRepository.class);
        ticketService = mock(TicketService.class);
        attachmentMapper = mock(AttachmentMapper.class);
        attachmentService = new AttachmentServiceImpl(attachmentRepository, ticketRepository, commentRepository,
                userRepository, ticketService, attachmentMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- uploadAttachment ---

    @Test
    void uploadAttachment_shouldCreateTicketAttachment_whenTicketIdGiven() {
        authenticateAs(1L, RoleName.USER);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        AttachmentResponse expected = anAttachmentResponse(200L, "file.png");
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(uploader));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(attachmentMapper.toResponse(any())).thenReturn(expected);

        AttachmentResponse result = attachmentService.uploadAttachment(10L, null, "storage-key", "file.png", "image/png", 1024L);

        assertEquals(expected, result);
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        Attachment saved = captor.getValue();
        assertEquals(ticket, saved.getTicket());
        assertNull(saved.getComment());
        assertEquals("storage-key", saved.getStorageKey());
        assertEquals("file.png", saved.getOriginalFilename());
        assertEquals("image/png", saved.getMimeType());
        assertEquals(1024L, saved.getSizeBytes());
        assertEquals(uploader, saved.getUploadedBy());
        verify(ticketService).validateTicketAccess(10L);
        verifyNoInteractions(commentRepository);
    }

    @Test
    void uploadAttachment_shouldCreateCommentAttachment_whenCommentIdGiven() {
        authenticateAs(1L, RoleName.USER);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Comment comment = aComment(50L, ticket, uploader);
        AttachmentResponse expected = anAttachmentResponse(201L, "notes.pdf");
        when(commentRepository.findById(50L)).thenReturn(Optional.of(comment));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(uploader));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(attachmentMapper.toResponse(any())).thenReturn(expected);

        AttachmentResponse result = attachmentService.uploadAttachment(10L, 50L, "storage-key-2", "notes.pdf", "application/pdf", 2048L);

        assertEquals(expected, result);
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        assertEquals(comment, captor.getValue().getComment());
        assertEquals(ticket, captor.getValue().getTicket());
        verify(ticketRepository).findById(10L);
    }

    @Test
    void uploadAttachment_shouldDeriveTicketFromComment_whenTicketIdIsNull() {
        authenticateAs(1L, RoleName.USER);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Comment comment = aComment(50L, ticket, uploader);
        AttachmentResponse expected = anAttachmentResponse(202L, "image.png");
        when(commentRepository.findById(50L)).thenReturn(Optional.of(comment));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(uploader));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(attachmentMapper.toResponse(any())).thenReturn(expected);

        AttachmentResponse result = attachmentService.uploadAttachment(null, 50L, "storage-key-3", "image.png", "image/png", 512L);

        assertEquals(expected, result);
        // ticketId was null - both calls below must use the id DERIVED from
        // comment.getTicket().getId() (10L), proving the derivation ran.
        verify(ticketService).validateTicketAccess(10L);
        verify(ticketRepository).findById(10L);
    }

    @Test
    void uploadAttachment_shouldThrowBadRequest_whenMimeTypeNotAllowed() {
        authenticateAs(1L, RoleName.USER);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(BadRequestException.class,
                () -> attachmentService.uploadAttachment(10L, null, "storage-key", "malware.exe", "application/x-msdownload", 1024L));

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void uploadAttachment_shouldThrowBadRequest_whenFileExceedsMaxSize() {
        authenticateAs(1L, RoleName.USER);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        long tooLarge = 11L * 1024 * 1024;

        assertThrows(BadRequestException.class,
                () -> attachmentService.uploadAttachment(10L, null, "storage-key", "huge.png", "image/png", tooLarge));

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void uploadAttachment_shouldThrowBadRequest_whenBothTicketIdAndCommentIdAreNull() {
        assertThrows(BadRequestException.class,
                () -> attachmentService.uploadAttachment(null, null, "storage-key", "file.png", "image/png", 1024L));

        verifyNoInteractions(ticketRepository, commentRepository, attachmentRepository, ticketService);
    }

    @Test
    void uploadAttachment_shouldThrowNotFound_whenTicketNotVisibleToCaller() {
        authenticateAs(1L, RoleName.USER);
        doThrow(new ResourceNotFoundException("Ticket", "id", 10L)).when(ticketService).validateTicketAccess(10L);

        assertThrows(ResourceNotFoundException.class,
                () -> attachmentService.uploadAttachment(10L, null, "storage-key", "file.png", "image/png", 1024L));

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void uploadAttachment_shouldThrowNotFound_whenCommentDoesNotExist() {
        when(commentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> attachmentService.uploadAttachment(null, 404L, "storage-key", "file.png", "image/png", 1024L));

        verifyNoInteractions(ticketService, ticketRepository, attachmentRepository);
    }

    // --- downloadAttachment ---

    @Test
    void downloadAttachment_shouldReturnMappedMetadata_whenTicketVisible() {
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Attachment attachment = anAttachment(200L, ticket, null, uploader);
        AttachmentResponse expected = anAttachmentResponse(200L, "file.png");
        when(attachmentRepository.findById(200L)).thenReturn(Optional.of(attachment));
        when(attachmentMapper.toResponse(attachment)).thenReturn(expected);

        AttachmentResponse result = attachmentService.downloadAttachment(200L);

        assertEquals(expected, result);
        verify(ticketService).validateTicketAccess(10L);
    }

    @Test
    void downloadAttachment_shouldThrowNotFound_whenTicketNotVisibleToCaller() {
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Attachment attachment = anAttachment(200L, ticket, null, uploader);
        when(attachmentRepository.findById(200L)).thenReturn(Optional.of(attachment));
        doThrow(new ResourceNotFoundException("Ticket", "id", 10L)).when(ticketService).validateTicketAccess(10L);

        assertThrows(ResourceNotFoundException.class, () -> attachmentService.downloadAttachment(200L));

        verifyNoInteractions(attachmentMapper);
    }

    @Test
    void downloadAttachment_shouldThrowNotFound_whenAttachmentDoesNotExist() {
        when(attachmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> attachmentService.downloadAttachment(404L));

        verifyNoInteractions(ticketService, attachmentMapper);
    }

    // --- deleteAttachment ---

    @Test
    void deleteAttachment_shouldDelete_whenCallerIsUploader() {
        authenticateAs(1L, RoleName.USER);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Attachment attachment = anAttachment(200L, ticket, null, uploader);
        when(attachmentRepository.findById(200L)).thenReturn(Optional.of(attachment));

        attachmentService.deleteAttachment(200L);

        verify(attachmentRepository).delete(attachment);
    }

    @Test
    void deleteAttachment_shouldDelete_whenCallerIsAdmin() {
        authenticateAs(99L, RoleName.ADMIN);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Attachment attachment = anAttachment(200L, ticket, null, uploader);
        when(attachmentRepository.findById(200L)).thenReturn(Optional.of(attachment));

        attachmentService.deleteAttachment(200L);

        verify(attachmentRepository).delete(attachment);
    }

    @Test
    void deleteAttachment_shouldThrowForbidden_whenCallerIsNotUploaderOrAdmin() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Attachment attachment = anAttachment(200L, ticket, null, uploader);
        when(attachmentRepository.findById(200L)).thenReturn(Optional.of(attachment));

        assertThrows(ForbiddenException.class, () -> attachmentService.deleteAttachment(200L));

        verify(attachmentRepository, never()).delete(any());
    }

    @Test
    void deleteAttachment_shouldThrowNotFound_whenAttachmentDoesNotExist() {
        when(attachmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> attachmentService.deleteAttachment(404L));

        verify(attachmentRepository, never()).delete(any());
    }

    /**
     * Visibility must gate ownership, not the reverse - same reasoning as
     * {@code downloadAttachment_shouldThrowNotFound_whenTicketNotVisibleToCaller}.
     * Without this check, a caller with no legitimate access to the parent
     * ticket could reach the ownership check below and learn from its 403
     * that this attachment id exists and who owns it.
     */
    @Test
    void deleteAttachment_shouldThrowNotFound_whenTicketNotVisibleToCaller() {
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Attachment attachment = anAttachment(200L, ticket, null, uploader);
        when(attachmentRepository.findById(200L)).thenReturn(Optional.of(attachment));
        doThrow(new ResourceNotFoundException("Ticket", "id", 10L)).when(ticketService).validateTicketAccess(10L);

        assertThrows(ResourceNotFoundException.class, () -> attachmentService.deleteAttachment(200L));

        verify(attachmentRepository, never()).delete(any());
    }

    // --- listAttachments ---

    @Test
    void listAttachments_shouldReturnFilteredList_whenCallerIsUser() {
        authenticateAs(1L, RoleName.USER);
        Pageable pageable = PageRequest.of(0, 20);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Attachment attachment = anAttachment(200L, ticket, null, uploader);
        AttachmentResponse response = anAttachmentResponse(200L, "file.png");
        Page<Attachment> page = new PageImpl<>(List.of(attachment), pageable, 1);
        when(attachmentRepository.findByTicketIdExcludingCommentVisibility(10L, CommentVisibility.INTERNAL, pageable)).thenReturn(page);
        when(attachmentMapper.toResponse(attachment)).thenReturn(response);

        Page<AttachmentResponse> result = attachmentService.listAttachments(10L, pageable);

        assertEquals(List.of(response), result.getContent());
        verify(attachmentRepository).findByTicketIdExcludingCommentVisibility(10L, CommentVisibility.INTERNAL, pageable);
        verify(attachmentRepository, never()).findByTicketId(any(), any());
    }

    @Test
    void listAttachments_shouldReturnFullList_whenCallerIsSupportEngineer() {
        authenticateAs(2L, RoleName.SUPPORT_ENGINEER);
        Pageable pageable = PageRequest.of(0, 20);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Attachment attachment = anAttachment(200L, ticket, null, uploader);
        Page<Attachment> page = new PageImpl<>(List.of(attachment), pageable, 1);
        when(attachmentRepository.findByTicketId(10L, pageable)).thenReturn(page);
        when(attachmentMapper.toResponse(attachment)).thenReturn(anAttachmentResponse(200L, "file.png"));

        Page<AttachmentResponse> result = attachmentService.listAttachments(10L, pageable);

        assertEquals(1, result.getTotalElements());
        verify(attachmentRepository).findByTicketId(10L, pageable);
        verify(attachmentRepository, never()).findByTicketIdExcludingCommentVisibility(any(), any(), any());
    }

    @Test
    void listAttachments_shouldReturnFullList_whenCallerIsAdmin() {
        authenticateAs(99L, RoleName.ADMIN);
        Pageable pageable = PageRequest.of(0, 20);
        User uploader = aUser(1L, RoleName.USER);
        Ticket ticket = aTicket(10L, uploader);
        Attachment attachment = anAttachment(200L, ticket, null, uploader);
        Page<Attachment> page = new PageImpl<>(List.of(attachment), pageable, 1);
        when(attachmentRepository.findByTicketId(10L, pageable)).thenReturn(page);
        when(attachmentMapper.toResponse(attachment)).thenReturn(anAttachmentResponse(200L, "file.png"));

        Page<AttachmentResponse> result = attachmentService.listAttachments(10L, pageable);

        assertEquals(1, result.getTotalElements());
        verify(attachmentRepository).findByTicketId(10L, pageable);
    }

    @Test
    void listAttachments_shouldThrowNotFound_whenTicketNotVisibleToCaller() {
        Pageable pageable = PageRequest.of(0, 20);
        doThrow(new ResourceNotFoundException("Ticket", "id", 10L)).when(ticketService).validateTicketAccess(10L);

        assertThrows(ResourceNotFoundException.class, () -> attachmentService.listAttachments(10L, pageable));

        verifyNoInteractions(attachmentRepository, attachmentMapper);
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

    private Comment aComment(Long id, Ticket ticket, User author) {
        Comment comment = new Comment(ticket, author, "content", CommentVisibility.PUBLIC);
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    private Attachment anAttachment(Long id, Ticket ticket, Comment comment, User uploadedBy) {
        Attachment attachment = new Attachment(ticket, comment, "storage-key", "file.png", "image/png", 1024L, uploadedBy);
        ReflectionTestUtils.setField(attachment, "id", id);
        return attachment;
    }

    private AttachmentResponse anAttachmentResponse(Long id, String filename) {
        return new AttachmentResponse(id, filename, "image/png", 1024L, "Uploader Name", Instant.now());
    }
}
