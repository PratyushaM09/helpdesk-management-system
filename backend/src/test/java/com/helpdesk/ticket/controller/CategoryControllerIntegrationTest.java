package com.helpdesk.ticket.controller;

import com.helpdesk.constant.ApiConstants;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.role.repository.RoleRepository;
import com.helpdesk.security.SecurityConstants;
import com.helpdesk.ticket.entity.Category;
import com.helpdesk.ticket.repository.CategoryRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full request-lifecycle proof, matching {@code AuthenticationControllerIntegrationTest}'s
 * convention: real login through {@code /auth/login} (never {@code @WithMockUser}
 * for this milestone), real {@link CategoryController}, real Service/Mapper/
 * Repository, real H2 database (test profile). {@code @Transactional} rolls
 * back every test method; the 8 categories {@code CategorySeeder} seeds at
 * application startup are the only rows any test sees change.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryControllerIntegrationTest {

    private static final String CATEGORIES_URL = ApiConstants.API_BASE_PATH + "/categories";
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
    private PasswordEncoder passwordEncoder;

    @Test
    void getActiveCategories_shouldReturn200WithSeededCategories_whenAuthenticated() throws Exception {
        Cookie accessCookie = loginAs("cat-user@example.com", RoleName.USER);

        mockMvc.perform(get(CATEGORIES_URL).cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(8));
    }

    @Test
    void getActiveCategories_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get(CATEGORIES_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void getActiveCategories_shouldExcludeDeactivatedCategory() throws Exception {
        Cookie accessCookie = loginAs("cat-user2@example.com", RoleName.USER);
        Category toDeactivate = categoryRepository.findByNameIgnoreCase("Software").orElseThrow();
        toDeactivate.deactivate();
        categoryRepository.save(toDeactivate);

        mockMvc.perform(get(CATEGORIES_URL).cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7));
    }

    @Test
    void getActiveCategories_shouldReturnEmptyArray_whenEveryCategoryDeactivated() throws Exception {
        Cookie accessCookie = loginAs("cat-user3@example.com", RoleName.USER);
        categoryRepository.findAll().forEach(category -> {
            category.deactivate();
            categoryRepository.save(category);
        });

        mockMvc.perform(get(CATEGORIES_URL).cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // --- helpers ---

    private Cookie loginAs(String email, RoleName roleName) throws Exception {
        persistUser(email, roleName);
        MvcResult result = mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);
    }

    private User persistUser(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User("Test User", email, passwordEncoder.encode(VALID_PASSWORD), role);
        return userRepository.save(user);
    }

    private String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
