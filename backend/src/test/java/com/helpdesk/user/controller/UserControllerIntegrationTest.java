package com.helpdesk.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.role.repository.RoleRepository;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full request-lifecycle proof: real {@link org.springframework.web.bind.annotation.RestController},
 * real Service/Mapper/Repository, real H2 database (test profile), real
 * {@code GlobalExceptionHandler} and security filter chain — see this
 * class's own reasoning note in the conversation for what's deliberately
 * not mocked. {@code @Transactional} rolls back every test method, so each
 * runs against a clean slate without manual cleanup.
 * <p>
 * {@code @WithMockUser(roles = "ADMIN")} (Phase 2, Milestone 3): every
 * {@code /api/v1/users/**} route is now {@code ADMIN}-only
 * ({@code SecurityConfig}). This simulates an authenticated Administrator
 * principal via {@code spring-security-test} without needing a real login
 * flow — {@code AuthenticationController} doesn't exist yet — exactly what
 * that dependency was added for.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class UserControllerIntegrationTest {

    private static final String BASE_URL = ApiConstants.API_BASE_PATH + "/users";
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
    private PasswordEncoder passwordEncoder;

    // --- POST /api/v1/users ---

    @Test
    void createUser_shouldReturn201AndPersistUser_whenRequestIsValid() throws Exception {
        String requestJson = createUserJson("Ada Lovelace", "ada@example.com", VALID_PASSWORD, "USER");

        MvcResult result = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.email").value("ada@example.com"))
                .andExpect(jsonPath("$.data.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
        User persisted = userRepository.findById(id).orElseThrow();
        assertNotEquals(VALID_PASSWORD, persisted.getPasswordHash());
        assertTrue(passwordEncoder.matches(VALID_PASSWORD, persisted.getPasswordHash()));
    }

    @Test
    void createUser_shouldReturn409_whenEmailAlreadyExists() throws Exception {
        persistUser("dup@example.com", RoleName.USER);
        String requestJson = createUserJson("Someone Else", "dup@example.com", VALID_PASSWORD, "USER");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void createUser_shouldReturn400_whenRequestFieldsAreInvalid() throws Exception {
        String invalidJson = """
                {"name": "", "email": "not-an-email", "password": "weak", "role": "USER"}
                """;

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    // --- GET /api/v1/users/{id} ---

    @Test
    void getUser_shouldReturn200_whenUserExists() throws Exception {
        User user = persistUser("get-me@example.com", RoleName.USER);

        mockMvc.perform(get(BASE_URL + "/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("get-me@example.com"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void getUser_shouldReturn404_whenUserDoesNotExist() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // --- GET /api/v1/users ---

    @Test
    void getUsers_shouldReturnPagedEnvelope_whenUsersExist() throws Exception {
        persistUser("list-one@example.com", RoleName.USER);
        persistUser("list-two@example.com", RoleName.USER);

        // 3, not 2: UserSeeder (Phase 2 Milestone 3) commits one bootstrap
        // ADMIN row at application startup, outside this test's own
        // @Transactional rollback boundary - same reason RoleControllerIntegrationTest
        // asserts against the 3 seeded roles rather than 0.
        mockMvc.perform(get(BASE_URL).param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void getUsers_shouldReturn400_whenSortFieldIsNotAllowed() throws Exception {
        mockMvc.perform(get(BASE_URL).param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    // --- PUT /api/v1/users/{id} ---

    @Test
    void updateUser_shouldReturn200AndPersistChanges_whenRequestIsValid() throws Exception {
        User user = persistUser("before-update@example.com", RoleName.USER);
        String requestJson = updateUserJson("Updated Name", "after-update@example.com", "SUPPORT_ENGINEER");

        mockMvc.perform(put(BASE_URL + "/{id}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.email").value("after-update@example.com"))
                .andExpect(jsonPath("$.data.role").value("SUPPORT_ENGINEER"));

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("Updated Name", persisted.getName());
        assertEquals(RoleName.SUPPORT_ENGINEER, persisted.getRole().getName());
    }

    @Test
    void updateUser_shouldReturn409_whenEmailBelongsToAnotherUser() throws Exception {
        persistUser("taken@example.com", RoleName.USER);
        User userToUpdate = persistUser("original@example.com", RoleName.USER);
        String requestJson = updateUserJson("Name", "taken@example.com", "USER");

        mockMvc.perform(put(BASE_URL + "/{id}", userToUpdate.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void updateUser_shouldReturn404_whenUserDoesNotExist() throws Exception {
        String requestJson = updateUserJson("Name", "missing@example.com", "USER");

        mockMvc.perform(put(BASE_URL + "/{id}", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateUser_shouldReturn400_whenRequestFieldsAreInvalid() throws Exception {
        User user = persistUser("invalid-update@example.com", RoleName.USER);
        String invalidJson = """
                {"name": "", "email": "not-an-email", "role": "USER"}
                """;

        mockMvc.perform(put(BASE_URL + "/{id}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // --- DELETE /api/v1/users/{id} ---

    @Test
    void deactivateUser_shouldReturn200AndSetStatusDeactivated_whenUserExists() throws Exception {
        User user = persistUser("to-deactivate@example.com", RoleName.USER);

        mockMvc.perform(delete(BASE_URL + "/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.DEACTIVATED, persisted.getStatus());
    }

    @Test
    void deactivateUser_shouldReturn404_whenUserDoesNotExist() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // --- helpers ---

    private User persistUser(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User("Test User", email, passwordEncoder.encode(VALID_PASSWORD), role);
        return userRepository.save(user);
    }

    private String createUserJson(String name, String email, String password, String role) {
        return """
                {"name": "%s", "email": "%s", "password": "%s", "role": "%s"}
                """.formatted(name, email, password, role);
    }

    private String updateUserJson(String name, String email, String role) {
        return """
                {"name": "%s", "email": "%s", "role": "%s"}
                """.formatted(name, email, role);
    }
}
