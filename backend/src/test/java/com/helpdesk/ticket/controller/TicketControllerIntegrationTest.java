package com.helpdesk.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import jakarta.persistence.EntityManager;
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

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full request-lifecycle proof, matching {@code AuthenticationControllerIntegrationTest}'s
 * convention: every test authenticates through the real {@code /auth/login}
 * endpoint (never {@code @WithMockUser} - this milestone's explicit
 * instruction), so the complete filter chain (JWT auth, CSRF, method
 * security) is exercised, not simulated. Real {@link TicketController}, real
 * Service/Mapper/Repository, real H2 database (test profile).
 * {@code @Transactional} rolls back every test method; seeded roles/categories
 * (via {@code RoleSeeder}/{@code CategorySeeder}) are the only rows shared
 * across tests.
 * <p>
 * Setup data (users, tickets) is persisted directly via repositories, the
 * same "repository for setup, HTTP for the action under test" convention
 * {@code RoleControllerIntegrationTest}/{@code UserControllerIntegrationTest}
 * already follow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketControllerIntegrationTest {

    private static final String TICKETS_URL = ApiConstants.API_BASE_PATH + "/tickets";
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    /** Backs {@link #persistTicket}'s placeholder ticket numbers - a fresh counter per test class instance is fine since {@code @Transactional} rolls back the rows anyway. */
    private final java.util.concurrent.atomic.AtomicLong seedTicketCounter = new java.util.concurrent.atomic.AtomicLong();

    // ============================================================
    // createTicket
    // ============================================================

    @Test
    void createTicket_shouldReturn201AndPersistTicket_whenUserRequestValid() throws Exception {
        AuthContext user = loginAs("create-user@example.com", RoleName.USER);
        Category category = aCategory();

        MvcResult result = mockMvc.perform(withCsrf(post(TICKETS_URL), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketJson(category.getId(), "MEDIUM")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.ticketNumber").exists())
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
        Ticket persisted = ticketRepository.findById(id).orElseThrow();
        assertEquals(TicketStatus.OPEN, persisted.getStatus());
        assertNull(persisted.getAssignedTo());
        assertEquals(user.user().getId(), persisted.getCreatedBy().getId());
    }

    @Test
    void createTicket_shouldReturn400_whenTitleBlank() throws Exception {
        AuthContext user = loginAs("create-invalid@example.com", RoleName.USER);
        Category category = aCategory();
        String invalidJson = """
                {"title": "", "description": "Description", "categoryId": %d, "priority": "MEDIUM"}
                """.formatted(category.getId());

        mockMvc.perform(withCsrf(post(TICKETS_URL), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void createTicket_shouldReturn400_whenCategoryInactive() throws Exception {
        AuthContext user = loginAs("create-inactive-category@example.com", RoleName.USER);
        Category category = aCategory();
        category.deactivate();
        categoryRepository.save(category);

        mockMvc.perform(withCsrf(post(TICKETS_URL), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketJson(category.getId(), "MEDIUM")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void createTicket_shouldReturn404_whenCategoryMissing() throws Exception {
        AuthContext user = loginAs("create-missing-category@example.com", RoleName.USER);

        mockMvc.perform(withCsrf(post(TICKETS_URL), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketJson(999_999L, "MEDIUM")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createTicket_shouldReturn403_whenCallerIsSupportEngineer() throws Exception {
        AuthContext engineer = loginAs("create-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        Category category = aCategory();

        mockMvc.perform(withCsrf(post(TICKETS_URL), engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketJson(category.getId(), "MEDIUM")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void createTicket_shouldReturn403_whenCallerIsAdmin() throws Exception {
        AuthContext admin = loginAs("create-admin@example.com", RoleName.ADMIN);
        Category category = aCategory();

        mockMvc.perform(withCsrf(post(TICKETS_URL), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketJson(category.getId(), "MEDIUM")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTicket_shouldReturn403_whenCsrfHeaderMissing() throws Exception {
        AuthContext user = loginAs("create-no-csrf@example.com", RoleName.USER);
        Category category = aCategory();

        mockMvc.perform(post(TICKETS_URL)
                        .cookie(user.accessCookie(), user.csrfCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketJson(category.getId(), "MEDIUM")))
                .andExpect(status().isForbidden());

        assertEquals(0, ticketRepository.count());
    }

    @Test
    void createTicket_shouldReturn401_whenAnonymous() throws Exception {
        Category category = aCategory();

        mockMvc.perform(post(TICKETS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketJson(category.getId(), "MEDIUM")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // ============================================================
    // getTicket
    // ============================================================

    @Test
    void getTicket_shouldReturn200_whenUserIsOwner() throws Exception {
        AuthContext user = loginAs("get-owner@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(get(TICKETS_URL + "/{id}", ticket.getId()).cookie(user.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ticket.getId()));
    }

    @Test
    void getTicket_shouldReturn404_whenUserIsNotOwner() throws Exception {
        AuthContext owner = loginAs("get-real-owner@example.com", RoleName.USER);
        AuthContext other = loginAs("get-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(owner.user(), TicketStatus.OPEN, null);

        mockMvc.perform(get(TICKETS_URL + "/{id}", ticket.getId()).cookie(other.accessCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getTicket_shouldReturn200_whenSupportEngineerIsAssigned() throws Exception {
        AuthContext creator = loginAs("get-creator1@example.com", RoleName.USER);
        AuthContext engineer = loginAs("get-engineer1@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());

        mockMvc.perform(get(TICKETS_URL + "/{id}", ticket.getId()).cookie(engineer.accessCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void getTicket_shouldReturn404_whenSupportEngineerNotAssigned() throws Exception {
        AuthContext creator = loginAs("get-creator2@example.com", RoleName.USER);
        AuthContext engineer = loginAs("get-engineer2@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(get(TICKETS_URL + "/{id}", ticket.getId()).cookie(engineer.accessCookie()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTicket_shouldReturn200_whenAdminRegardlessOfOwnership() throws Exception {
        AuthContext creator = loginAs("get-creator3@example.com", RoleName.USER);
        AuthContext admin = loginAs("get-admin1@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(get(TICKETS_URL + "/{id}", ticket.getId()).cookie(admin.accessCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void getTicket_shouldReturn404_whenTicketDoesNotExist() throws Exception {
        AuthContext admin = loginAs("get-admin2@example.com", RoleName.ADMIN);

        mockMvc.perform(get(TICKETS_URL + "/{id}", 999_999L).cookie(admin.accessCookie()))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    // getTickets (list)
    // ============================================================

    @Test
    void getTickets_shouldReturnOnlyOwnTickets_forUser() throws Exception {
        AuthContext user = loginAs("list-user@example.com", RoleName.USER);
        AuthContext otherUser = loginAs("list-other@example.com", RoleName.USER);
        persistTicket(user.user(), TicketStatus.OPEN, null);
        persistTicket(user.user(), TicketStatus.OPEN, null);
        persistTicket(otherUser.user(), TicketStatus.OPEN, null);

        mockMvc.perform(get(TICKETS_URL).cookie(user.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getTickets_shouldReturnOnlyAssignedTickets_forSupportEngineer() throws Exception {
        AuthContext creator = loginAs("list-creator@example.com", RoleName.USER);
        AuthContext engineer = loginAs("list-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        AuthContext otherEngineer = loginAs("list-other-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());
        persistTicket(creator.user(), TicketStatus.ASSIGNED, otherEngineer.user());

        mockMvc.perform(get(TICKETS_URL).cookie(engineer.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getTickets_shouldReturnAllTickets_forAdmin() throws Exception {
        AuthContext creator = loginAs("list-creator2@example.com", RoleName.USER);
        AuthContext admin = loginAs("list-admin@example.com", RoleName.ADMIN);
        persistTicket(creator.user(), TicketStatus.OPEN, null);
        persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(get(TICKETS_URL).cookie(admin.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getTickets_shouldRespectPagination() throws Exception {
        AuthContext user = loginAs("list-page-user@example.com", RoleName.USER);
        persistTicket(user.user(), TicketStatus.OPEN, null);
        persistTicket(user.user(), TicketStatus.OPEN, null);
        persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(get(TICKETS_URL).cookie(user.accessCookie()).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void getTickets_shouldReturn400_whenSortFieldInvalid() throws Exception {
        AuthContext user = loginAs("list-sort-user@example.com", RoleName.USER);

        mockMvc.perform(get(TICKETS_URL).cookie(user.accessCookie()).param("sort", "description,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    // ============================================================
    // updateTicket
    // ============================================================

    @Test
    void updateTicket_shouldReturn200AndPersistChanges_whenUserOwnsOpenTicket() throws Exception {
        AuthContext user = loginAs("update-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(put(TICKETS_URL + "/{id}", ticket.getId()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTicketJson("New Title", "New Description", ticket.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("New Title"));

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals("New Title", persisted.getTitle());
        assertEquals("New Description", persisted.getDescription());
    }

    @Test
    void updateTicket_shouldReturn200_whenAdminEditsClosedTicket() throws Exception {
        AuthContext creator = loginAs("update-creator@example.com", RoleName.USER);
        AuthContext admin = loginAs("update-admin@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.CLOSED, null);

        mockMvc.perform(withCsrf(put(TICKETS_URL + "/{id}", ticket.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTicketJson("Admin Edit", "Admin Description", ticket.getVersion())))
                .andExpect(status().isOk());
    }

    @Test
    void updateTicket_shouldReturn403_whenCallerIsSupportEngineer() throws Exception {
        AuthContext creator = loginAs("update-creator2@example.com", RoleName.USER);
        AuthContext engineer = loginAs("update-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());

        mockMvc.perform(withCsrf(put(TICKETS_URL + "/{id}", ticket.getId()), engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTicketJson("Nope", "Nope", ticket.getVersion())))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateTicket_shouldReturn409_whenTicketStatusNotEditable() throws Exception {
        AuthContext user = loginAs("update-resolved-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.RESOLVED, null);

        mockMvc.perform(withCsrf(put(TICKETS_URL + "/{id}", ticket.getId()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTicketJson("Nope", "Nope", ticket.getVersion())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void updateTicket_shouldReturn409_whenVersionStale() throws Exception {
        AuthContext user = loginAs("update-stale-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(put(TICKETS_URL + "/{id}", ticket.getId()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTicketJson("Nope", "Nope", ticket.getVersion() + 99)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateTicket_shouldReturn404_whenTicketHidden() throws Exception {
        AuthContext owner = loginAs("update-real-owner@example.com", RoleName.USER);
        AuthContext stranger = loginAs("update-stranger@example.com", RoleName.USER);
        Ticket ticket = persistTicket(owner.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(put(TICKETS_URL + "/{id}", ticket.getId()), stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTicketJson("Nope", "Nope", ticket.getVersion())))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    // assignTicket
    // ============================================================

    @Test
    void assignTicket_shouldReturn200AndPersistAssignment_whenAdmin() throws Exception {
        AuthContext creator = loginAs("assign-creator@example.com", RoleName.USER);
        AuthContext admin = loginAs("assign-admin@example.com", RoleName.ADMIN);
        AuthContext engineer = loginAs("assign-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/assign", ticket.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTicketJson(engineer.user().getId(), ticket.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.assignedToName").value(engineer.user().getName()));

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.ASSIGNED, persisted.getStatus());
        assertEquals(engineer.user().getId(), persisted.getAssignedTo().getId());
    }

    @Test
    void assignTicket_shouldReturn403_whenCallerIsUser() throws Exception {
        AuthContext creator = loginAs("assign-user-caller@example.com", RoleName.USER);
        AuthContext engineer = loginAs("assign-engineer2@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/assign", ticket.getId()), creator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTicketJson(engineer.user().getId(), ticket.getVersion())))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignTicket_shouldReturn403_whenCallerIsSupportEngineer() throws Exception {
        AuthContext creator = loginAs("assign-creator2@example.com", RoleName.USER);
        AuthContext engineer = loginAs("assign-engineer-caller@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/assign", ticket.getId()), engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTicketJson(engineer.user().getId(), ticket.getVersion())))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignTicket_shouldReturn400_whenAgentIsNotSupportEngineer() throws Exception {
        AuthContext creator = loginAs("assign-creator3@example.com", RoleName.USER);
        AuthContext admin = loginAs("assign-admin2@example.com", RoleName.ADMIN);
        AuthContext notAnEngineer = loginAs("assign-not-engineer@example.com", RoleName.USER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/assign", ticket.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTicketJson(notAnEngineer.user().getId(), ticket.getVersion())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignTicket_shouldReturn409_whenTicketNotOpen() throws Exception {
        AuthContext creator = loginAs("assign-creator4@example.com", RoleName.USER);
        AuthContext admin = loginAs("assign-admin3@example.com", RoleName.ADMIN);
        AuthContext engineer = loginAs("assign-engineer3@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/assign", ticket.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTicketJson(engineer.user().getId(), ticket.getVersion())))
                .andExpect(status().isConflict());
    }

    // ============================================================
    // reassignTicket
    // ============================================================

    @Test
    void reassignTicket_shouldReturn200AndPersistNewAssignment_whenAdmin() throws Exception {
        AuthContext creator = loginAs("reassign-creator@example.com", RoleName.USER);
        AuthContext admin = loginAs("reassign-admin@example.com", RoleName.ADMIN);
        AuthContext oldEngineer = loginAs("reassign-old-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        AuthContext newEngineer = loginAs("reassign-new-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, oldEngineer.user());

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/reassign", ticket.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTicketJson(newEngineer.user().getId(), ticket.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedToName").value(newEngineer.user().getName()));

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(newEngineer.user().getId(), persisted.getAssignedTo().getId());
    }

    @Test
    void reassignTicket_shouldReturn409_whenTicketResolved() throws Exception {
        AuthContext creator = loginAs("reassign-creator2@example.com", RoleName.USER);
        AuthContext admin = loginAs("reassign-admin2@example.com", RoleName.ADMIN);
        AuthContext engineer = loginAs("reassign-engineer2@example.com", RoleName.SUPPORT_ENGINEER);
        AuthContext newEngineer = loginAs("reassign-new-engineer2@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.RESOLVED, engineer.user());

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/reassign", ticket.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTicketJson(newEngineer.user().getId(), ticket.getVersion())))
                .andExpect(status().isConflict());
    }

    @Test
    void reassignTicket_shouldReturn400_whenAgentInvalid() throws Exception {
        AuthContext creator = loginAs("reassign-creator3@example.com", RoleName.USER);
        AuthContext admin = loginAs("reassign-admin3@example.com", RoleName.ADMIN);
        AuthContext engineer = loginAs("reassign-engineer3@example.com", RoleName.SUPPORT_ENGINEER);
        AuthContext notAnEngineer = loginAs("reassign-not-engineer@example.com", RoleName.USER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/reassign", ticket.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignTicketJson(notAnEngineer.user().getId(), ticket.getVersion())))
                .andExpect(status().isBadRequest());
    }

    // ============================================================
    // changeStatus - legal transitions
    // ============================================================

    @Test
    void changeStatus_shouldTransition_assignedToInProgress() throws Exception {
        assertLegalTransitionViaHttp(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);
    }

    @Test
    void changeStatus_shouldTransition_inProgressToWaitingForCustomer() throws Exception {
        assertLegalTransitionViaHttp(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_FOR_CUSTOMER);
    }

    @Test
    void changeStatus_shouldTransition_waitingForCustomerToInProgress() throws Exception {
        assertLegalTransitionViaHttp(TicketStatus.WAITING_FOR_CUSTOMER, TicketStatus.IN_PROGRESS);
    }

    @Test
    void changeStatus_shouldTransition_inProgressToResolved() throws Exception {
        assertLegalTransitionViaHttp(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);
    }

    @Test
    void changeStatus_shouldTransition_resolvedToClosed() throws Exception {
        // CLOSED confirmation is the creator's (or admin's) action, not the
        // engineer's - exercised as the creator (USER) rather than through
        // the shared engineer-actor helper below.
        AuthContext creator = loginAs("status-close-creator@example.com", RoleName.USER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.RESOLVED, null);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/status", ticket.getId()), creator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeStatusJson("CLOSED", "confirmed", ticket.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.CLOSED, persisted.getStatus());
        assertNotNull(persisted.getClosedAt());
    }

    /** Drives one legal transition through HTTP as the assigned SUPPORT_ENGINEER, verifying persistence. */
    private void assertLegalTransitionViaHttp(TicketStatus from, TicketStatus to) throws Exception {
        AuthContext creator = loginAs("status-creator-" + from + "-" + to + "@example.com", RoleName.USER);
        AuthContext engineer = loginAs("status-engineer-" + from + "-" + to + "@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), from, engineer.user());

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/status", ticket.getId()), engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeStatusJson(to.name(), "progressing", ticket.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(to.name()));

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(to, persisted.getStatus());
    }

    // ============================================================
    // changeStatus - illegal transitions
    // ============================================================

    @Test
    void changeStatus_shouldReturn409_whenOpenToClosed() throws Exception {
        assertIllegalTransitionViaHttp(TicketStatus.OPEN, TicketStatus.CLOSED);
    }

    @Test
    void changeStatus_shouldReturn409_whenWaitingForCustomerToClosed() throws Exception {
        assertIllegalTransitionViaHttp(TicketStatus.WAITING_FOR_CUSTOMER, TicketStatus.CLOSED);
    }

    @Test
    void changeStatus_shouldReturn409_whenClosedToInProgress() throws Exception {
        assertIllegalTransitionViaHttp(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS);
    }

    /** ADMIN as caller in every case - proves the workflow graph itself is enforced end-to-end, not just the actor check. */
    private void assertIllegalTransitionViaHttp(TicketStatus from, TicketStatus to) throws Exception {
        AuthContext creator = loginAs("illegal-creator-" + from + "-" + to + "@example.com", RoleName.USER);
        AuthContext admin = loginAs("illegal-admin-" + from + "-" + to + "@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(creator.user(), from, null);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/status", ticket.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeStatusJson(to.name(), null, ticket.getVersion())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(from, persisted.getStatus());
    }

    // ============================================================
    // reopenTicket
    // ============================================================

    @Test
    void reopenTicket_shouldReturn200_whenUserWithinWindow() throws Exception {
        AuthContext user = loginAs("reopen-user@example.com", RoleName.USER);
        AuthContext engineer = loginAs("reopen-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.RESOLVED, engineer.user());
        ticket.setResolvedAt(Instant.now().minus(Duration.ofDays(2)));
        ticketRepository.save(ticket);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/reopen", ticket.getId()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reopenTicketJson("issue recurred")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.assignedToName").value(engineer.user().getName()));

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.ASSIGNED, persisted.getStatus());
        assertEquals(engineer.user().getId(), persisted.getAssignedTo().getId());
    }

    @Test
    void reopenTicket_shouldReturn409_whenUserWindowExpired() throws Exception {
        AuthContext user = loginAs("reopen-expired-user@example.com", RoleName.USER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.RESOLVED, null);
        ticket.setResolvedAt(Instant.now().minus(Duration.ofDays(8)));
        ticketRepository.save(ticket);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/reopen", ticket.getId()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reopenTicketJson("too late")))
                .andExpect(status().isConflict());
    }

    @Test
    void reopenTicket_shouldReturn200_whenAdminRegardlessOfWindow() throws Exception {
        AuthContext user = loginAs("reopen-admin-user@example.com", RoleName.USER);
        AuthContext admin = loginAs("reopen-admin@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(user.user(), TicketStatus.RESOLVED, null);
        ticket.setResolvedAt(Instant.now().minus(Duration.ofDays(30)));
        ticketRepository.save(ticket);

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/reopen", ticket.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reopenTicketJson("admin override")))
                .andExpect(status().isOk());
    }

    @Test
    void reopenTicket_shouldReturn403_whenSupportEngineer() throws Exception {
        AuthContext user = loginAs("reopen-eng-user@example.com", RoleName.USER);
        AuthContext engineer = loginAs("reopen-eng@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(user.user(), TicketStatus.RESOLVED, engineer.user());

        mockMvc.perform(withCsrf(post(TICKETS_URL + "/{id}/reopen", ticket.getId()), engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reopenTicketJson("attempt")))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // deleteTicket
    // ============================================================

    @Test
    void deleteTicket_shouldReturn200AndSoftDelete_whenAdmin() throws Exception {
        AuthContext creator = loginAs("delete-creator@example.com", RoleName.USER);
        AuthContext admin = loginAs("delete-admin@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(delete(TICKETS_URL + "/{id}", ticket.getId()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertNotNull(persisted.getDeletedAt());
        assertEquals(admin.user().getId(), persisted.getDeletedBy().getId());
    }

    @Test
    void deleteTicket_shouldReturn403_whenUser() throws Exception {
        AuthContext creator = loginAs("delete-user-caller@example.com", RoleName.USER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(delete(TICKETS_URL + "/{id}", ticket.getId()), creator))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTicket_shouldReturn403_whenSupportEngineer() throws Exception {
        AuthContext creator = loginAs("delete-creator2@example.com", RoleName.USER);
        AuthContext engineer = loginAs("delete-engineer@example.com", RoleName.SUPPORT_ENGINEER);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.ASSIGNED, engineer.user());

        mockMvc.perform(withCsrf(delete(TICKETS_URL + "/{id}", ticket.getId()), engineer))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTicket_shouldMakeTicketInaccessible_afterDelete() throws Exception {
        AuthContext creator = loginAs("delete-inaccessible-creator@example.com", RoleName.USER);
        AuthContext admin = loginAs("delete-inaccessible-admin@example.com", RoleName.ADMIN);
        Ticket ticket = persistTicket(creator.user(), TicketStatus.OPEN, null);

        mockMvc.perform(withCsrf(delete(TICKETS_URL + "/{id}", ticket.getId()), admin))
                .andExpect(status().isOk());

        // @Transactional wraps this whole test in one Hibernate session, so
        // without forcing a fresh query here, a same-session findById would
        // return the already-loaded (now soft-deleted) entity instance
        // straight from the first-level cache, never re-running the SQL
        // @SQLRestriction filters on - an artifact of one test method
        // spanning two "requests," not something a real client ever
        // observes (each real HTTP request gets its own transaction/session).
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(TICKETS_URL + "/{id}", ticket.getId()).cookie(admin.accessCookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(TICKETS_URL + "/{id}", ticket.getId()).cookie(creator.accessCookie()))
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
        // ticket_number is VARCHAR(20) (matches the real "HD-2026-000001"
        // format, 14 chars) - a zero-padded counter keeps this fixture's
        // placeholder number well within that, unlike a raw System.nanoTime().
        String ticketNumber = "HD-SEED-%06d".formatted(seedTicketCounter.incrementAndGet());
        Ticket ticket = new Ticket(ticketNumber, "Seed Title", "Seed Description", category, TicketPriority.MEDIUM, creator);
        ticket.setStatus(status);
        if (assignedTo != null) {
            ticket.setAssignedTo(assignedTo);
        }
        return ticketRepository.save(ticket);
    }

    private String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private String createTicketJson(Long categoryId, String priority) {
        return """
                {"title": "Printer not working", "description": "The office printer is jammed.", "categoryId": %d, "priority": "%s"}
                """.formatted(categoryId, priority);
    }

    private String updateTicketJson(String title, String description, long version) {
        return """
                {"title": "%s", "description": "%s", "version": %d}
                """.formatted(title, description, version);
    }

    private String assignTicketJson(long agentId, long version) {
        return """
                {"agentId": %d, "version": %d}
                """.formatted(agentId, version);
    }

    private String changeStatusJson(String targetStatus, String comment, long version) {
        String commentJson = comment != null ? "\"%s\"".formatted(comment) : "null";
        return """
                {"targetStatus": "%s", "comment": %s, "version": %d}
                """.formatted(targetStatus, commentJson, version);
    }

    private String reopenTicketJson(String reason) {
        return """
                {"reason": "%s"}
                """.formatted(reason);
    }
}
