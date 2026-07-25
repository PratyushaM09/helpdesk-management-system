package com.helpdesk.attachment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.attachment.entity.Attachment;
import com.helpdesk.attachment.repository.AttachmentRepository;
import com.helpdesk.comment.entity.Comment;
import com.helpdesk.comment.entity.CommentVisibility;
import com.helpdesk.comment.repository.CommentRepository;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.role.repository.RoleRepository;
import com.helpdesk.security.SecurityConstants;
import com.helpdesk.ticket.entity.Category;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.CategoryRepository;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full request-lifecycle proof, matching {@code TicketControllerIntegrationTest}'s
 * convention: every test authenticates through the real {@code /auth/login}
 * endpoint. Real {@link AttachmentController}, real Service/Mapper/
 * Repository, real H2 database (test profile). {@code @Transactional} rolls
 * back every test method.
 * <p>
 * Uploads use {@link MockMultipartFile} with Spring Test's
 * {@code multipart(...)} request builder - no real bytes are written
 * anywhere (no {@code FileStorageService} exists yet); only the metadata
 * path is exercised, matching {@code AttachmentService}'s own scope.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttachmentControllerIntegrationTest {

    private static final String TICKETS_URL = ApiConstants.API_BASE_PATH + "/tickets";
    private static final String COMMENTS_URL = ApiConstants.API_BASE_PATH + "/comments";
    private static final String ATTACHMENTS_URL = ApiConstants.API_BASE_PATH + "/attachments";
    private static final String AUTH_URL = ApiConstants.API_BASE_PATH + "/auth";
    private static final String VALID_PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final AtomicLong seedTicketCounter = new AtomicLong();

    // ============================================================
    // uploadTicketAttachment
    // ============================================================

    @Test
    void uploadTicketAttachment_shouldReturn201_whenUserUploads() throws Exception {
        AuthContext user = loginAs("upload-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        MvcResult result = mockMvc.perform(withCsrf(multipart(TICKETS_URL + "/{id}/attachments", ticket.getId()), user)
                        .file(aFile("image/png")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.originalFilename").value("document.png"))
                .andExpect(jsonPath("$.data.mimeType").value("image/png"))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist())
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
        Attachment persisted = attachmentRepository.findById(id).orElseThrow();
        assertEquals(ticket.getId(), persisted.getTicket().getId());
        assertEquals(user.user().getId(), persisted.getUploadedBy().getId());
    }

    @Test
    void uploadTicketAttachment_shouldReturn201_whenSupportEngineerUploads() throws Exception {
        AuthContext creator = loginAs("upload-creator1@example.com", RoleName.USER);
        AuthContext engineer = loginAs("upload-engineer1@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());

        mockMvc.perform(withCsrf(multipart(TICKETS_URL + "/{id}/attachments", ticket.getId()), engineer)
                        .file(aFile("application/pdf")))
                .andExpect(status().isCreated());
    }

    @Test
    void uploadTicketAttachment_shouldReturn201_whenAdminUploads() throws Exception {
        AuthContext creator = loginAs("upload-creator2@example.com", RoleName.USER);
        AuthContext admin = loginAs("upload-admin1@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(multipart(TICKETS_URL + "/{id}/attachments", ticket.getId()), admin)
                        .file(aFile("image/jpeg")))
                .andExpect(status().isCreated());
    }

    @Test
    void uploadTicketAttachment_shouldReturn404_whenTicketHidden() throws Exception {
        AuthContext owner = loginAs("upload-real-owner@example.com", RoleName.USER);
        AuthContext stranger = loginAs("upload-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(owner.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(multipart(TICKETS_URL + "/{id}/attachments", ticket.getId()), stranger)
                        .file(aFile("image/png")))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadTicketAttachment_shouldReturn404_whenTicketNotFound() throws Exception {
        AuthContext user = loginAs("upload-missing-ticket@example.com", RoleName.USER);

        mockMvc.perform(withCsrf(multipart(TICKETS_URL + "/{id}/attachments", 999_999L), user)
                        .file(aFile("image/png")))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadTicketAttachment_shouldReturn400_whenMimeTypeInvalid() throws Exception {
        AuthContext user = loginAs("upload-bad-mime@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(multipart(TICKETS_URL + "/{id}/attachments", ticket.getId()), user)
                        .file(aFile("application/x-msdownload")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        assertEquals(0, attachmentRepository.count());
    }

    @Test
    void uploadTicketAttachment_shouldReturn400_whenFileOversized() throws Exception {
        AuthContext user = loginAs("upload-oversized@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        MockMultipartFile oversizedFile = new MockMultipartFile("file", "huge.png", "image/png", new byte[11 * 1024 * 1024]);

        mockMvc.perform(withCsrf(multipart(TICKETS_URL + "/{id}/attachments", ticket.getId()), user)
                        .file(oversizedFile))
                .andExpect(status().isBadRequest());

        assertEquals(0, attachmentRepository.count());
    }

    @Test
    void uploadTicketAttachment_shouldReturn401_whenAnonymous() throws Exception {
        AuthContext user = loginAs("upload-anon-setup@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(multipart(TICKETS_URL + "/{id}/attachments", ticket.getId()).file(aFile("image/png")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadTicketAttachment_shouldReturn403_whenCsrfMissing() throws Exception {
        AuthContext user = loginAs("upload-no-csrf@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(multipart(TICKETS_URL + "/{id}/attachments", ticket.getId())
                        .file(aFile("image/png"))
                        .cookie(user.accessCookie(), user.csrfCookie()))
                .andExpect(status().isForbidden());

        assertEquals(0, attachmentRepository.count());
    }

    // ============================================================
    // uploadCommentAttachment
    // ============================================================

    @Test
    void uploadCommentAttachment_shouldReturn201AndLinkToComment_whenValid() throws Exception {
        AuthContext user = loginAs("upload-comment-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        Comment comment = persistComment(ticket, user.user(), CommentVisibility.PUBLIC);

        MvcResult result = mockMvc.perform(withCsrf(multipart(COMMENTS_URL + "/{id}/attachments", comment.getId()), user)
                        .file(aFile("application/pdf")))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
        Attachment persisted = attachmentRepository.findById(id).orElseThrow();
        assertEquals(comment.getId(), persisted.getComment().getId());
        assertEquals(ticket.getId(), persisted.getTicket().getId());
    }

    @Test
    void uploadCommentAttachment_shouldReturn404_whenCommentInvalid() throws Exception {
        AuthContext user = loginAs("upload-comment-invalid@example.com", RoleName.USER);

        mockMvc.perform(withCsrf(multipart(COMMENTS_URL + "/{id}/attachments", 999_999L), user)
                        .file(aFile("application/pdf")))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadCommentAttachment_shouldReturn404_whenHiddenTicketThroughComment() throws Exception {
        AuthContext owner = loginAs("upload-comment-real-owner@example.com", RoleName.USER);
        AuthContext stranger = loginAs("upload-comment-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(owner.user(), TicketStatus.OPEN, null);
        Comment comment = persistComment(ticket, owner.user(), CommentVisibility.PUBLIC);

        mockMvc.perform(withCsrf(multipart(COMMENTS_URL + "/{id}/attachments", comment.getId()), stranger)
                        .file(aFile("application/pdf")))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadCommentAttachment_shouldSucceed_forInternalComment_whenCallerIsStaff() throws Exception {
        AuthContext creator = loginAs("upload-comment-creator@example.com", RoleName.USER);
        AuthContext engineer = loginAs("upload-comment-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());
        Comment internalComment = persistComment(ticket, engineer.user(), CommentVisibility.INTERNAL);

        mockMvc.perform(withCsrf(multipart(COMMENTS_URL + "/{id}/attachments", internalComment.getId()), engineer)
                        .file(aFile("application/pdf")))
                .andExpect(status().isCreated());
    }

    // ============================================================
    // listAttachments
    // ============================================================

    @Test
    void listAttachments_shouldExcludeInternalCommentAttachments_forUser() throws Exception {
        AuthContext creator = loginAs("list-att-creator1@example.com", RoleName.USER);
        AuthContext engineer = loginAs("list-att-engineer1@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());
        persistAttachment(ticket, null, creator.user());
        Comment internalComment = persistComment(ticket, engineer.user(), CommentVisibility.INTERNAL);
        persistAttachment(ticket, internalComment, engineer.user());

        mockMvc.perform(get(TICKETS_URL + "/{id}/attachments", ticket.getId()).cookie(creator.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void listAttachments_shouldIncludeAll_forSupportEngineer() throws Exception {
        AuthContext creator = loginAs("list-att-creator2@example.com", RoleName.USER);
        AuthContext engineer = loginAs("list-att-engineer2@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());
        persistAttachment(ticket, null, creator.user());
        Comment internalComment = persistComment(ticket, engineer.user(), CommentVisibility.INTERNAL);
        persistAttachment(ticket, internalComment, engineer.user());

        mockMvc.perform(get(TICKETS_URL + "/{id}/attachments", ticket.getId()).cookie(engineer.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void listAttachments_shouldIncludeAll_forAdmin() throws Exception {
        AuthContext creator = loginAs("list-att-creator3@example.com", RoleName.USER);
        AuthContext admin = loginAs("list-att-admin@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);
        persistAttachment(ticket, null, creator.user());
        Comment internalComment = persistComment(ticket, creator.user(), CommentVisibility.INTERNAL);
        persistAttachment(ticket, internalComment, creator.user());

        mockMvc.perform(get(TICKETS_URL + "/{id}/attachments", ticket.getId()).cookie(admin.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void listAttachments_shouldRespectPagination() throws Exception {
        AuthContext user = loginAs("list-att-page-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        persistAttachment(ticket, null, user.user());
        persistAttachment(ticket, null, user.user());
        persistAttachment(ticket, null, user.user());

        mockMvc.perform(get(TICKETS_URL + "/{id}/attachments", ticket.getId())
                        .cookie(user.accessCookie()).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    // ============================================================
    // downloadAttachment
    // ============================================================

    @Test
    void downloadAttachment_shouldReturn200_whenAuthorized() throws Exception {
        AuthContext user = loginAs("download-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        Attachment attachment = persistAttachment(ticket, null, user.user());

        mockMvc.perform(get(ATTACHMENTS_URL + "/{id}", attachment.getId()).cookie(user.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("document.png"))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());
    }

    @Test
    void downloadAttachment_shouldReturn404_whenTicketHidden() throws Exception {
        AuthContext owner = loginAs("download-real-owner@example.com", RoleName.USER);
        AuthContext stranger = loginAs("download-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(owner.user(), TicketStatus.OPEN, null);
        Attachment attachment = persistAttachment(ticket, null, owner.user());

        mockMvc.perform(get(ATTACHMENTS_URL + "/{id}", attachment.getId()).cookie(stranger.accessCookie()))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadAttachment_shouldReturn404_whenAttachmentNotFound() throws Exception {
        AuthContext user = loginAs("download-missing@example.com", RoleName.USER);

        mockMvc.perform(get(ATTACHMENTS_URL + "/{id}", 999_999L).cookie(user.accessCookie()))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    // deleteAttachment
    // ============================================================

    @Test
    void deleteAttachment_shouldReturn200_whenUploaderDeletes() throws Exception {
        AuthContext user = loginAs("delete-att-uploader@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        Attachment attachment = persistAttachment(ticket, null, user.user());

        mockMvc.perform(withCsrf(delete(ATTACHMENTS_URL + "/{id}", attachment.getId()), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(attachmentRepository.findById(attachment.getId()).isEmpty());
    }

    @Test
    void deleteAttachment_shouldReturn200_whenAdminDeletes() throws Exception {
        AuthContext user = loginAs("delete-att-owner@example.com", RoleName.USER);
        AuthContext admin = loginAs("delete-att-admin@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        Attachment attachment = persistAttachment(ticket, null, user.user());

        mockMvc.perform(withCsrf(delete(ATTACHMENTS_URL + "/{id}", attachment.getId()), admin))
                .andExpect(status().isOk());

        assertTrue(attachmentRepository.findById(attachment.getId()).isEmpty());
    }

    @Test
    void deleteAttachment_shouldReturn403_whenCallerCanSeeTicketButIsNotUploaderOrAdmin() throws Exception {
        AuthContext uploader = loginAs("delete-att-real-uploader@example.com", RoleName.USER);
        AuthContext engineer = loginAs("delete-att-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(uploader.user(), TicketStatus.ASSIGNED, engineer.user());
        Attachment attachment = persistAttachment(ticket, null, uploader.user());

        mockMvc.perform(withCsrf(delete(ATTACHMENTS_URL + "/{id}", attachment.getId()), engineer))
                .andExpect(status().isForbidden());

        assertTrue(attachmentRepository.findById(attachment.getId()).isPresent());
    }

    /**
     * Visibility must gate ownership, not the reverse: a caller with no
     * legitimate access to the parent ticket must never learn (via a 403)
     * that this attachment id exists and who owns it - the same 404-not-403
     * anti-enumeration rule every other attachment endpoint already follows.
     */
    @Test
    void deleteAttachment_shouldReturn404_whenTicketNotVisibleToCaller() throws Exception {
        AuthContext uploader = loginAs("delete-att-hidden-uploader@example.com", RoleName.USER);
        AuthContext stranger = loginAs("delete-att-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(uploader.user(), TicketStatus.OPEN, null);
        Attachment attachment = persistAttachment(ticket, null, uploader.user());

        mockMvc.perform(withCsrf(delete(ATTACHMENTS_URL + "/{id}", attachment.getId()), stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        assertTrue(attachmentRepository.findById(attachment.getId()).isPresent());
    }

    @Test
    void deleteAttachment_shouldReturn404_whenAttachmentNotFound() throws Exception {
        AuthContext user = loginAs("delete-att-missing@example.com", RoleName.USER);

        mockMvc.perform(withCsrf(delete(ATTACHMENTS_URL + "/{id}", 999_999L), user))
                .andExpect(status().isNotFound());
    }

    // --- fixtures ---

    private record AuthContext(User user, Cookie accessCookie, Cookie csrfCookie) {
    }

    private AuthContext loginAs(String email, RoleName roleName) throws Exception {
        User user = persistUser(email, roleName);
        MvcResult result = mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access = result.getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);
        Cookie csrf = result.getResponse().getCookie(SecurityConstants.CSRF_COOKIE);
        return new AuthContext(user, access, csrf);
    }

    private User persistUser(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User("Test User", email, passwordEncoder.encode(VALID_PASSWORD), role);
        return userRepository.save(user);
    }

    private MockMultipartHttpServletRequestBuilder withCsrf(MockMultipartHttpServletRequestBuilder builder, AuthContext auth) {
        builder.cookie(auth.accessCookie(), auth.csrfCookie());
        builder.header(SecurityConstants.CSRF_HEADER, auth.csrfCookie().getValue());
        return builder;
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder, AuthContext auth) {
        return builder.cookie(auth.accessCookie(), auth.csrfCookie())
                .header(SecurityConstants.CSRF_HEADER, auth.csrfCookie().getValue());
    }

    private MockMultipartFile aFile(String contentType) {
        return new MockMultipartFile("file", "document.png", contentType, "file-bytes".getBytes());
    }

    private Category aCategory() {
        return categoryRepository.findByNameIgnoreCase("Software").orElseThrow();
    }

    private Ticket persistTicket(User creator, TicketStatus status, User assignedTo) {
        Category category = aCategory();
        String ticketNumber = "HD-SEED-%06d".formatted(seedTicketCounter.incrementAndGet());
        Ticket ticket = new Ticket(ticketNumber, "Seed Title", "Seed Description", category, TicketPriority.MEDIUM, creator);
        ticket.setStatus(status);
        if (assignedTo != null) {
            ticket.setAssignedTo(assignedTo);
        }
        return ticketRepository.save(ticket);
    }

    private Comment persistComment(Ticket ticket, User author, CommentVisibility visibility) {
        Comment comment = new Comment(ticket, author, "Seed comment content", visibility);
        return commentRepository.save(comment);
    }

    private Attachment persistAttachment(Ticket ticket, Comment comment, User uploadedBy) {
        Attachment attachment = new Attachment(ticket, comment, "seed-storage-key-" + seedTicketCounter.incrementAndGet(),
                "document.png", "image/png", 1024L, uploadedBy);
        return attachmentRepository.save(attachment);
    }

    private String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
