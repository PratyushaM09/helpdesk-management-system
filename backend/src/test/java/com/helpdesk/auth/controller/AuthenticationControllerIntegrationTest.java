package com.helpdesk.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.auth.entity.RefreshToken;
import com.helpdesk.auth.repository.RefreshTokenRepository;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.role.repository.RoleRepository;
import com.helpdesk.security.SecurityConstants;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full request-lifecycle proof for the Authentication module, matching
 * {@code UserControllerIntegrationTest}/{@code RoleControllerIntegrationTest}'s
 * conventions: real {@link AuthenticationController}, real
 * {@code AuthenticationService}/{@code JwtService}/{@code CookieService},
 * real filter chain, real H2 database. Unlike those two classes (which
 * simulate an admin via {@code @WithMockUser}), every test here authenticates
 * through the real {@code /auth/login} endpoint — this is the one place the
 * genuine, end-to-end JWT issuance/verification path is exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationControllerIntegrationTest {

    private static final String AUTH_URL = ApiConstants.API_BASE_PATH + "/auth";
    private static final String USERS_URL = ApiConstants.API_BASE_PATH + "/users";
    private static final String ROLES_URL = ApiConstants.API_BASE_PATH + "/roles";
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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // --- login ---

    @Test
    void login_shouldReturn200AndSetCookiesWithExpectedFlags_whenCredentialsValid() throws Exception {
        User user = persistUser("login-success@example.com", RoleName.USER);

        MvcResult result = mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn();

        Cookie accessCookie = result.getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);
        Cookie refreshCookie = result.getResponse().getCookie(SecurityConstants.REFRESH_TOKEN_COOKIE);
        Cookie csrfCookie = result.getResponse().getCookie(SecurityConstants.CSRF_COOKIE);
        assertNotNull(accessCookie);
        assertNotNull(refreshCookie);
        assertNotNull(csrfCookie);
        assertTrue(accessCookie.isHttpOnly());
        assertTrue(accessCookie.getSecure());
        assertTrue(refreshCookie.isHttpOnly());
        assertTrue(csrfCookie.getSecure());
        assertEquals(false, csrfCookie.isHttpOnly());
    }

    @Test
    void login_shouldReturn401_whenPasswordWrong() throws Exception {
        User user = persistUser("wrong-password@example.com", RoleName.USER);

        mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), "WrongPassword1!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void login_shouldReturn401WithSameBodyAsWrongPassword_whenEmailUnknown() throws Exception {
        persistUser("known@example.com", RoleName.USER);

        MvcResult wrongPasswordResult = mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("known@example.com", "WrongPassword1!")))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult unknownEmailResult = mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nobody-here@example.com", "WrongPassword1!")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode wrongPasswordBody = objectMapper.readTree(wrongPasswordResult.getResponse().getContentAsString());
        JsonNode unknownEmailBody = objectMapper.readTree(unknownEmailResult.getResponse().getContentAsString());
        assertEquals(wrongPasswordBody.get("errorCode"), unknownEmailBody.get("errorCode"));
        assertEquals(wrongPasswordBody.get("message"), unknownEmailBody.get("message"));
    }

    @Test
    void login_shouldReturn423_whenAccountLockedAfterFiveFailedAttempts() throws Exception {
        User user = persistUser("lockout@example.com", RoleName.USER);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(AUTH_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(user.getEmail(), "WrongPassword1!")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), VALID_PASSWORD)))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    // --- refresh ---

    @Test
    void refresh_shouldReturn200AndRotateTokens_whenValid() throws Exception {
        User user = persistUser("refresh-success@example.com", RoleName.USER);
        Cookie originalRefreshCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.REFRESH_TOKEN_COOKIE);

        MvcResult refreshResult = mockMvc.perform(post(AUTH_URL + "/refresh").cookie(originalRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(user.getEmail()))
                .andReturn();

        Cookie newRefreshCookie = refreshResult.getResponse().getCookie(SecurityConstants.REFRESH_TOKEN_COOKIE);
        assertNotNull(newRefreshCookie);
        assertNotEquals(originalRefreshCookie.getValue(), newRefreshCookie.getValue());
        assertNull(refreshResult.getResponse().getCookie(SecurityConstants.CSRF_COOKIE));
    }

    @Test
    void refresh_shouldReturn401_whenTokenExpired() throws Exception {
        User user = persistUser("refresh-expired@example.com", RoleName.USER);
        Cookie refreshCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.REFRESH_TOKEN_COOKIE);
        RefreshToken persisted = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow();
        ReflectionTestUtils.setField(persisted, "expiresAt", Instant.now().minusSeconds(1));
        refreshTokenRepository.save(persisted);

        mockMvc.perform(post(AUTH_URL + "/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_shouldReturn401_whenTokenAlreadyRevokedViaLogout() throws Exception {
        User user = persistUser("refresh-revoked@example.com", RoleName.USER);
        MvcResult loginResult = login(user.getEmail());
        Cookie accessCookie = loginResult.getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);
        Cookie refreshCookie = loginResult.getResponse().getCookie(SecurityConstants.REFRESH_TOKEN_COOKIE);

        // /auth/logout itself requires authentication (07-Security-Architecture.md
        // §3.3), hence the access token cookie here too - not just the refresh one.
        mockMvc.perform(post(AUTH_URL + "/logout").cookie(accessCookie, refreshCookie)).andExpect(status().isOk());

        mockMvc.perform(post(AUTH_URL + "/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_shouldReturn401AndKillEntireFamily_whenTokenReused() throws Exception {
        User user = persistUser("reuse-detection@example.com", RoleName.USER);
        Cookie originalRefreshCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.REFRESH_TOKEN_COOKIE);

        MvcResult firstRefresh = mockMvc.perform(post(AUTH_URL + "/refresh").cookie(originalRefreshCookie))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedRefreshCookie = firstRefresh.getResponse().getCookie(SecurityConstants.REFRESH_TOKEN_COOKIE);

        mockMvc.perform(post(AUTH_URL + "/refresh").cookie(originalRefreshCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(AUTH_URL + "/refresh").cookie(rotatedRefreshCookie))
                .andExpect(status().isUnauthorized());
    }

    // --- logout ---

    @Test
    void logout_shouldReturn200AndInvalidateToken_whenCookiePresent() throws Exception {
        User user = persistUser("logout-success@example.com", RoleName.USER);
        MvcResult loginResult = login(user.getEmail());
        Cookie accessCookie = loginResult.getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);
        Cookie refreshCookie = loginResult.getResponse().getCookie(SecurityConstants.REFRESH_TOKEN_COOKIE);

        MvcResult logoutResult = mockMvc.perform(post(AUTH_URL + "/logout").cookie(accessCookie, refreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie clearedAccessCookie = logoutResult.getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);
        assertEquals(0, clearedAccessCookie.getMaxAge());

        mockMvc.perform(post(AUTH_URL + "/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The scenario the controller's null-refresh-cookie fallback actually
     * exists for: an authenticated caller (real access token) with no
     * refresh cookie at all - a Bearer-only client, per SDR-002's
     * alternative path. Calling logout with literally no cookies at all
     * would correctly 401 at the authorization layer before ever reaching
     * the controller, since /auth/logout itself requires authentication.
     */
    @Test
    void logout_shouldReturn200_whenAuthenticatedButNoRefreshCookiePresent() throws Exception {
        User user = persistUser("logout-no-refresh-cookie@example.com", RoleName.USER);
        Cookie accessCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(post(AUTH_URL + "/logout").cookie(accessCookie))
                .andExpect(status().isOk());
    }

    // --- JWT authentication / RBAC / CSRF, end to end ---

    @Test
    void jwtAuthentication_shouldAllowAccessToProtectedEndpoint_whenRealAccessTokenCookiePresent() throws Exception {
        User admin = persistUser("real-admin@example.com", RoleName.ADMIN);
        Cookie accessCookie = login(admin.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(get(USERS_URL).cookie(accessCookie))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedAccess_shouldReturn401_whenNoTokenPresentOnProtectedRoute() throws Exception {
        mockMvc.perform(get(USERS_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void forbiddenAccess_shouldReturn403_whenAuthenticatedAsWrongRole() throws Exception {
        User regularUser = persistUser("plain-user@example.com", RoleName.USER);
        Cookie accessCookie = login(regularUser.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(get(USERS_URL).cookie(accessCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void csrf_shouldSucceed_whenHeaderMatchesCookie() throws Exception {
        User admin = persistUser("csrf-success-admin@example.com", RoleName.ADMIN);
        MvcResult loginResult = login(admin.getEmail());
        Cookie accessCookie = loginResult.getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);
        Cookie csrfCookie = loginResult.getResponse().getCookie(SecurityConstants.CSRF_COOKIE);
        Role targetRole = roleRepository.findByName(RoleName.SUPPORT_ENGINEER).orElseThrow();

        mockMvc.perform(put(ROLES_URL + "/{id}", targetRole.getId())
                        .cookie(accessCookie, csrfCookie)
                        .header(SecurityConstants.CSRF_HEADER, csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRoleJson("Updated via CSRF-protected request")))
                .andExpect(status().isOk());
    }

    @Test
    void csrf_shouldReturn403AndLeaveResourceUnchanged_whenHeaderMissing() throws Exception {
        User admin = persistUser("csrf-fail-admin@example.com", RoleName.ADMIN);
        MvcResult loginResult = login(admin.getEmail());
        Cookie accessCookie = loginResult.getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);
        Cookie csrfCookie = loginResult.getResponse().getCookie(SecurityConstants.CSRF_COOKIE);
        Role targetRole = roleRepository.findByName(RoleName.SUPPORT_ENGINEER).orElseThrow();
        String originalDescription = targetRole.getDescription();

        mockMvc.perform(put(ROLES_URL + "/{id}", targetRole.getId())
                        .cookie(accessCookie, csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRoleJson("Should never be applied")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        Role reloaded = roleRepository.findById(targetRole.getId()).orElseThrow();
        assertEquals(originalDescription, reloaded.getDescription());
    }

    @Test
    void rbac_rolesEndpoint_shouldReturn403_forNonAdminUser() throws Exception {
        User regularUser = persistUser("roles-rbac-user@example.com", RoleName.USER);
        Cookie accessCookie = login(regularUser.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(get(ROLES_URL).cookie(accessCookie))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private User persistUser(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User("Test User", email, passwordEncoder.encode(VALID_PASSWORD), role);
        user.setStatus(UserStatus.VERIFIED);
        return userRepository.save(user);
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private String updateRoleJson(String description) {
        return """
                {"description": "%s"}
                """.formatted(description);
    }
}
