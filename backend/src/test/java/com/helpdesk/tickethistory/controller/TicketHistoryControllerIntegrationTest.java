package com.helpdesk.tickethistory.controller;

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
import com.helpdesk.tickethistory.entity.TicketHistoryAction;
import com.helpdesk.tickethistory.repository.TicketHistoryRepository;
import com.helpdesk.tickethistory.entity.TicketHistory;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full request-lifecycle proof, matching {@code TicketControllerIntegrationTest}'s
 * convention: every test authenticates through the real {@code /auth/login}
 * endpoint. Real {@link TicketHistoryController}, real Service/Mapper/
 * Repository, real H2 database (test profile). {@code @Transactional} rolls
 * back every test method.
 * <p>
 * History rows are persisted directly via {@code TicketHistoryRepository}
 * for test setup (the same "repository for setup, HTTP for the action under
 * test" convention every other integration test in this codebase follows) -
 * no write endpoint exists to create them through HTTP (ADR-0006:
 * append-only, written only by {@code TicketService} as a side effect).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketHistoryControllerIntegrationTest {

    private static final String TICKETS_URL = ApiConstants.API_BASE_PATH + "/tickets";
    private static final String AUTH_URL = ApiConstants.API_BASE_PATH + "/auth";
    private static final String VALID_PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketHistoryRepository ticketHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final AtomicLong seedTicketCounter = new AtomicLong();

    @Test
    void getHistory_shouldHideInternalEntries_forUser() throws Exception {
        AuthContext user = loginAs("history-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        persistHistory(ticket, user.user(), TicketHistoryAction.STATUS_CHANGED, false);
        persistHistory(ticket, user.user(), TicketHistoryAction.COMMENT_ADDED, true);

        mockMvc.perform(get(TICKETS_URL + "/{id}/history", ticket.getId()).cookie(user.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("STATUS_CHANGED"));
    }

    @Test
    void getHistory_shouldReturnFullHistory_forSupportEngineer() throws Exception {
        AuthContext creator = loginAs("history-creator1@example.com", RoleName.USER);
        AuthContext engineer = loginAs("history-engineer1@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());
        persistHistory(ticket, engineer.user(), TicketHistoryAction.ASSIGNED, false);
        persistHistory(ticket, engineer.user(), TicketHistoryAction.COMMENT_ADDED, true);

        mockMvc.perform(get(TICKETS_URL + "/{id}/history", ticket.getId()).cookie(engineer.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getHistory_shouldReturnFullHistory_forAdmin() throws Exception {
        AuthContext creator = loginAs("history-creator2@example.com", RoleName.USER);
        AuthContext admin = loginAs("history-admin@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);
        persistHistory(ticket, admin.user(), TicketHistoryAction.CREATED, false);
        persistHistory(ticket, admin.user(), TicketHistoryAction.COMMENT_ADDED, true);

        mockMvc.perform(get(TICKETS_URL + "/{id}/history", ticket.getId()).cookie(admin.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getHistory_shouldReturn404_whenTicketHidden() throws Exception {
        AuthContext owner = loginAs("history-real-owner@example.com", RoleName.USER);
        AuthContext stranger = loginAs("history-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(owner.user(), TicketStatus.OPEN, null);
        persistHistory(ticket, owner.user(), TicketHistoryAction.CREATED, false);

        mockMvc.perform(get(TICKETS_URL + "/{id}/history", ticket.getId()).cookie(stranger.accessCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getHistory_shouldReturn404_whenTicketNotFound() throws Exception {
        AuthContext user = loginAs("history-missing-ticket@example.com", RoleName.USER);

        mockMvc.perform(get(TICKETS_URL + "/{id}/history", 999_999L).cookie(user.accessCookie()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistory_shouldRespectPagination() throws Exception {
        AuthContext user = loginAs("history-page-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        persistHistory(ticket, user.user(), TicketHistoryAction.CREATED, false);
        persistHistory(ticket, user.user(), TicketHistoryAction.STATUS_CHANGED, false);
        persistHistory(ticket, user.user(), TicketHistoryAction.STATUS_CHANGED, false);

        mockMvc.perform(get(TICKETS_URL + "/{id}/history", ticket.getId())
                        .cookie(user.accessCookie()).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void getHistory_shouldRespectSorting() throws Exception {
        AuthContext user = loginAs("history-sort-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);
        TicketHistory first = persistHistory(ticket, user.user(), TicketHistoryAction.CREATED, false);
        persistHistory(ticket, user.user(), TicketHistoryAction.STATUS_CHANGED, false);

        mockMvc.perform(get(TICKETS_URL + "/{id}/history", ticket.getId())
                        .cookie(user.accessCookie()).param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(first.getId()));
    }

    @Test
    void getHistory_shouldReturn401_whenAnonymous() throws Exception {
        AuthContext user = loginAs("history-anon-setup@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(get(TICKETS_URL + "/{id}/history", ticket.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
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

    private TicketHistory persistHistory(Ticket ticket, User actor, TicketHistoryAction action, boolean internal) {
        TicketHistory history = new TicketHistory(ticket, actor, action, "OLD", "NEW", null, internal);
        return ticketHistoryRepository.save(history);
    }

    private String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
