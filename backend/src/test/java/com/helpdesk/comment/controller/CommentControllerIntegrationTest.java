package com.helpdesk.comment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full request-lifecycle proof, matching {@code TicketControllerIntegrationTest}'s
 * convention: every test authenticates through the real {@code /auth/login}
 * endpoint (never {@code @WithMockUser}), so the complete filter chain (JWT
 * auth, CSRF, method security) runs for real. Real {@link CommentController},
 * real Service/Mapper/Repository, real H2 database (test profile).
 * {@code @Transactional} rolls back every test method.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommentControllerIntegrationTest {

    private static final String TICKETS_URL = ApiConstants.API_BASE_PATH + "/tickets";
    private static final String COMMENTS_URL = ApiConstants.API_BASE_PATH + "/comments";
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
    private PasswordEncoder passwordEncoder;

    private final AtomicLong seedTicketCounter = new AtomicLong();

    // ============================================================
    // addComment
    // ============================================================

    @Test
    void addComment_shouldReturn201_whenUserCreatesPublicComment() throws Exception {
        AuthContext user = loginAs("comment-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        MvcResult result = mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/comments", ticket.getId()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentJson("Please help me", "PUBLIC")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.content").value("Please help me"))
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
        Comment persisted = commentRepository.findById(id).orElseThrow();
        assertEquals(CommentVisibility.PUBLIC, persisted.getVisibility());
        assertEquals(user.user().getId(), persisted.getAuthor().getId());
    }

    @Test
    void addComment_shouldReturn201_whenSupportEngineerCreatesPublicComment() throws Exception {
        AuthContext creator = loginAs("comment-creator1@example.com", RoleName.USER);
        AuthContext engineer = loginAs("comment-engineer1@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/comments", ticket.getId()), engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentJson("Working on it", "PUBLIC")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"));
    }

    @Test
    void addComment_shouldReturn201_whenSupportEngineerCreatesInternalComment() throws Exception {
        AuthContext creator = loginAs("comment-creator2@example.com", RoleName.USER);
        AuthContext engineer = loginAs("comment-engineer2@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/comments", ticket.getId()), engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentJson("Internal note - escalation needed", "INTERNAL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.visibility").value("INTERNAL"));
    }

    @Test
    void addComment_shouldReturn201_whenAdminCreatesInternalComment() throws Exception {
        AuthContext creator = loginAs("comment-creator3@example.com", RoleName.USER);
        AuthContext admin = loginAs("comment-admin1@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/comments", ticket.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentJson("Admin internal note", "INTERNAL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.visibility").value("INTERNAL"));
    }

    @Test
    void addComment_shouldReturn403_whenUserAttemptsInternalComment() throws Exception {
        AuthContext user = loginAs("comment-user-internal@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/comments", ticket.getId()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentJson("Sneaky internal note", "INTERNAL")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        assertEquals(0, commentRepository.count());
    }

    @Test
    void addComment_shouldReturn404_whenTicketHidden() throws Exception {
        AuthContext owner = loginAs("comment-real-owner@example.com", RoleName.USER);
        AuthContext stranger = loginAs("comment-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(owner.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/comments", ticket.getId()), stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentJson("Not my ticket", "PUBLIC")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void addComment_shouldReturn404_whenTicketNotFound() throws Exception {
        AuthContext user = loginAs("comment-missing-ticket@example.com", RoleName.USER);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/comments", 999_999L), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentJson("Ghost ticket", "PUBLIC")))
                .andExpect(status().isNotFound());
    }

    @Test
    void addComment_shouldReturn400_whenContentBlank() throws Exception {
        AuthContext user = loginAs("comment-blank@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/comments", ticket.getId()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentJson("", "PUBLIC")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void addComment_shouldReturn401_whenAnonymous() throws Exception {
        AuthContext user = loginAs("comment-anon-setup@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(post(TICKETS_URL + "/{id}/comments", ticket.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentJson("No auth", "PUBLIC")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void addComment_shouldReturn403_whenCsrfMissing() throws Exception {
        AuthContext user = loginAs("comment-no-csrf@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(post(TICKETS_URL + "/{id}/comments", ticket.getId())
                        .cookie(user.accessCookie(), user.csrfCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCommentJson("No CSRF header", "PUBLIC")))
                .andExpect(status().isForbidden());

        assertEquals(0, commentRepository.count());
    }

    // ============================================================
    // updateComment
    // ============================================================

    @Test
    void updateComment_shouldReturn200_whenAuthorUpdates() throws Exception {
        AuthContext user = loginAs("update-comment-author@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        Comment comment = persistComment(ticket, user.user(), CommentVisibility.PUBLIC);

        mockMvc.perform(withCsrf(put(COMMENTS_URL + "/{id}", comment.getId()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateCommentJson("Edited content")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Edited content"))
                .andExpect(jsonPath("$.data.edited").value(true));

        Comment persisted = commentRepository.findById(comment.getId()).orElseThrow();
        assertEquals("Edited content", persisted.getContent());
        assertTrue(persisted.isEdited());
    }

    @Test
    void updateComment_shouldReturn200_whenAdminUpdates() throws Exception {
        AuthContext user = loginAs("update-comment-owner@example.com", RoleName.USER);
        AuthContext admin = loginAs("update-comment-admin@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        Comment comment = persistComment(ticket, user.user(), CommentVisibility.PUBLIC);

        mockMvc.perform(withCsrf(put(COMMENTS_URL + "/{id}", comment.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateCommentJson("Edited by admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Edited by admin"));
    }

    @Test
    void updateComment_shouldReturn403_whenNonAuthor() throws Exception {
        AuthContext author = loginAs("update-comment-real-author@example.com", RoleName.USER);
        AuthContext stranger = loginAs("update-comment-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(author.user(), TicketStatus.OPEN, null);
        Comment comment = persistComment(ticket, author.user(), CommentVisibility.PUBLIC);

        mockMvc.perform(withCsrf(put(COMMENTS_URL + "/{id}", comment.getId()), stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateCommentJson("Hijack attempt")))
                .andExpect(status().isForbidden());

        Comment persisted = commentRepository.findById(comment.getId()).orElseThrow();
        assertEquals(comment.getContent(), persisted.getContent());
    }

    @Test
    void updateComment_shouldReturn404_whenCommentNotFound() throws Exception {
        AuthContext user = loginAs("update-comment-missing@example.com", RoleName.USER);

        mockMvc.perform(withCsrf(put(COMMENTS_URL + "/{id}", 999_999L), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateCommentJson("Ghost comment")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateComment_shouldReturn400_whenContentBlank() throws Exception {
        AuthContext user = loginAs("update-comment-blank@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        Comment comment = persistComment(ticket, user.user(), CommentVisibility.PUBLIC);

        mockMvc.perform(withCsrf(put(COMMENTS_URL + "/{id}", comment.getId()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateCommentJson("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void updateComment_shouldReturn401_whenAnonymous() throws Exception {
        AuthContext user = loginAs("update-comment-anon-setup@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        Comment comment = persistComment(ticket, user.user(), CommentVisibility.PUBLIC);

        mockMvc.perform(put(COMMENTS_URL + "/{id}", comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateCommentJson("No auth")))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // deleteComment
    // ============================================================

    @Test
    void deleteComment_shouldReturn200_whenAuthorDeletes() throws Exception {
        AuthContext user = loginAs("delete-comment-author@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        Comment comment = persistComment(ticket, user.user(), CommentVisibility.PUBLIC);

        mockMvc.perform(withCsrf(delete(COMMENTS_URL + "/{id}", comment.getId()), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(commentRepository.findById(comment.getId()).isEmpty());
    }

    @Test
    void deleteComment_shouldReturn200_whenAdminDeletes() throws Exception {
        AuthContext user = loginAs("delete-comment-owner@example.com", RoleName.USER);
        AuthContext admin = loginAs("delete-comment-admin@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        Comment comment = persistComment(ticket, user.user(), CommentVisibility.PUBLIC);

        mockMvc.perform(withCsrf(delete(COMMENTS_URL + "/{id}", comment.getId()), admin))
                .andExpect(status().isOk());

        assertTrue(commentRepository.findById(comment.getId()).isEmpty());
    }

    @Test
    void deleteComment_shouldReturn403_whenNonAuthor() throws Exception {
        AuthContext author = loginAs("delete-comment-real-author@example.com", RoleName.USER);
        AuthContext stranger = loginAs("delete-comment-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(author.user(), TicketStatus.OPEN, null);
        Comment comment = persistComment(ticket, author.user(), CommentVisibility.PUBLIC);

        mockMvc.perform(withCsrf(delete(COMMENTS_URL + "/{id}", comment.getId()), stranger))
                .andExpect(status().isForbidden());

        assertTrue(commentRepository.findById(comment.getId()).isPresent());
    }

    @Test
    void deleteComment_shouldReturn404_whenCommentNotFound() throws Exception {
        AuthContext user = loginAs("delete-comment-missing@example.com", RoleName.USER);

        mockMvc.perform(withCsrf(delete(COMMENTS_URL + "/{id}", 999_999L), user))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    // getComments
    // ============================================================

    @Test
    void getComments_shouldReturnOnlyPublic_forUser() throws Exception {
        AuthContext user = loginAs("list-comments-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        persistComment(ticket, user.user(), CommentVisibility.PUBLIC);
        persistComment(ticket, user.user(), CommentVisibility.INTERNAL);

        mockMvc.perform(get(TICKETS_URL + "/{id}/comments", ticket.getId()).cookie(user.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].visibility").value("PUBLIC"));
    }

    @Test
    void getComments_shouldReturnPublicAndInternal_forSupportEngineer() throws Exception {
        AuthContext creator = loginAs("list-comments-creator1@example.com", RoleName.USER);
        AuthContext engineer = loginAs("list-comments-engineer1@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());
        persistComment(ticket, creator.user(), CommentVisibility.PUBLIC);
        persistComment(ticket, engineer.user(), CommentVisibility.INTERNAL);

        mockMvc.perform(get(TICKETS_URL + "/{id}/comments", ticket.getId()).cookie(engineer.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getComments_shouldReturnFullThread_forAdmin() throws Exception {
        AuthContext creator = loginAs("list-comments-creator2@example.com", RoleName.USER);
        AuthContext admin = loginAs("list-comments-admin@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);
        persistComment(ticket, creator.user(), CommentVisibility.PUBLIC);
        persistComment(ticket, admin.user(), CommentVisibility.INTERNAL);

        mockMvc.perform(get(TICKETS_URL + "/{id}/comments", ticket.getId()).cookie(admin.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getComments_shouldRespectPagination() throws Exception {
        AuthContext user = loginAs("list-comments-page-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        persistComment(ticket, user.user(), CommentVisibility.PUBLIC);
        persistComment(ticket, user.user(), CommentVisibility.PUBLIC);
        persistComment(ticket, user.user(), CommentVisibility.PUBLIC);

        mockMvc.perform(get(TICKETS_URL + "/{id}/comments", ticket.getId())
                        .cookie(user.accessCookie()).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void getComments_shouldReturn404_whenTicketHidden() throws Exception {
        AuthContext owner = loginAs("list-comments-real-owner@example.com", RoleName.USER);
        AuthContext stranger = loginAs("list-comments-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(owner.user(), TicketStatus.OPEN, null);

        mockMvc.perform(get(TICKETS_URL + "/{id}/comments", ticket.getId()).cookie(stranger.accessCookie()))
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

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder, AuthContext auth) {
        return builder.cookie(auth.accessCookie(), auth.csrfCookie())
                .header(SecurityConstants.CSRF_HEADER, auth.csrfCookie().getValue());
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

    private String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private String createCommentJson(String content, String visibility) {
        String visibilityJson = visibility != null ? "\"%s\"".formatted(visibility) : "null";
        return """
                {"content": "%s", "visibility": %s}
                """.formatted(content, visibilityJson);
    }

    private String updateCommentJson(String content) {
        return """
                {"content": "%s"}
                """.formatted(content);
    }
}
