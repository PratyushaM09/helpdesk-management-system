package com.helpdesk.account.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.account.entity.EmailVerificationToken;
import com.helpdesk.account.entity.PasswordResetToken;
import com.helpdesk.account.repository.EmailVerificationTokenRepository;
import com.helpdesk.account.repository.PasswordResetTokenRepository;
import com.helpdesk.account.service.SecureTokenService;
import com.helpdesk.constant.ApiConstants;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full request-lifecycle proof for the Account module, matching
 * {@code AuthenticationControllerIntegrationTest}'s conventions: real
 * {@link AccountController}, real {@code AccountService}/{@code
 * AuthenticationService}, real filter chain, real H2 database.
 * <p>
 * Deliberately carries no class-level {@code @WithMockUser}, unlike {@code
 * UserControllerIntegrationTest}/{@code RoleControllerIntegrationTest}:
 * {@code AccountServiceImpl.loadAuthenticatedUser()} hard-casts the security
 * principal to {@code UserPrincipal}, a type {@code @WithMockUser} never
 * produces (it builds a {@code org.springframework.security.core.userdetails.User}
 * instead) — using it on any self-service endpoint here would fail with a
 * {@code ClassCastException} rather than proving anything. Every self-service
 * test (`/me`, `/profile`, `/password`, `/resend-verification`) therefore
 * authenticates through the real {@code /auth/login} endpoint, the same
 * approach {@code AuthenticationControllerIntegrationTest} already
 * established. The two admin endpoints (`/activate`, `/deactivate`) never
 * touch the principal at all — only the path {@code userId} — so
 * {@code @WithMockUser} is safe and used there, per-method, matching
 * {@code UserControllerIntegrationTest}'s convention.
 * <p>
 * State-changing self-service requests attach only the access-token cookie,
 * never a CSRF cookie — {@code CsrfValidationFilter} only enforces the
 * double-submit check when a {@code csrf_token} cookie is already present on
 * the request, so omitting it entirely (rather than fetching and pairing it
 * with the {@code X-CSRF-Token} header) is a legitimate, simpler way to reach
 * these endpoints without a dedicated CSRF test, which is already covered
 * end-to-end by {@code AuthenticationControllerIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountControllerIntegrationTest {

    private static final String ACCOUNT_URL = ApiConstants.API_BASE_PATH + "/account";
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private SecureTokenService secureTokenService;

    // --- GET /me ---

    @Test
    void me_shouldReturn200WithProfile_whenAuthenticated() throws Exception {
        User user = persistUser("me-success@example.com", RoleName.USER);
        Cookie accessCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(get(ACCOUNT_URL + "/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void me_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get(ACCOUNT_URL + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // --- PUT /profile ---

    @Test
    void updateProfile_shouldReturn200AndPersistNewName_whenAuthenticated() throws Exception {
        User user = persistUser("update-profile-success@example.com", RoleName.USER);
        Cookie accessCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(put(ACCOUNT_URL + "/profile")
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfileJson("New Name")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("New Name", reloaded.getName());
    }

    @Test
    void updateProfile_shouldReturn400_whenNameBlank() throws Exception {
        User user = persistUser("update-profile-invalid@example.com", RoleName.USER);
        Cookie accessCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(put(ACCOUNT_URL + "/profile")
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfileJson("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void updateProfile_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(put(ACCOUNT_URL + "/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfileJson("New Name")))
                .andExpect(status().isUnauthorized());
    }

    // --- PUT /password ---

    @Test
    void changePassword_shouldReturn200AndAllowLoginWithNewPassword_whenCurrentPasswordCorrect() throws Exception {
        User user = persistUser("change-password-success@example.com", RoleName.USER);
        Cookie accessCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);
        String newPassword = "NewStr0ng!Pass1";

        mockMvc.perform(put(ACCOUNT_URL + "/password")
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson(VALID_PASSWORD, newPassword, newPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), newPassword)))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_shouldReturn400_whenCurrentPasswordIncorrect() throws Exception {
        User user = persistUser("change-password-wrong-current@example.com", RoleName.USER);
        Cookie accessCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(put(ACCOUNT_URL + "/password")
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson("WrongCurrent1!", "NewStr0ng!Pass1", "NewStr0ng!Pass1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void changePassword_shouldReturn400_whenConfirmationDoesNotMatch() throws Exception {
        User user = persistUser("change-password-mismatch@example.com", RoleName.USER);
        Cookie accessCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(put(ACCOUNT_URL + "/password")
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson(VALID_PASSWORD, "NewStr0ng!Pass1", "Mismatch1!Aa")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void changePassword_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(put(ACCOUNT_URL + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson(VALID_PASSWORD, "NewStr0ng!Pass1", "NewStr0ng!Pass1")))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /forgot-password ---

    @Test
    void forgotPassword_shouldReturn200_whenEmailExists() throws Exception {
        User user = persistUser("forgot-password-exists@example.com", RoleName.USER);

        mockMvc.perform(post(ACCOUNT_URL + "/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotPasswordJson(user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void forgotPassword_shouldReturnSameResponseAsExistingEmail_whenEmailUnknown() throws Exception {
        persistUser("forgot-password-known@example.com", RoleName.USER);

        MvcResult existingResult = mockMvc.perform(post(ACCOUNT_URL + "/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotPasswordJson("forgot-password-known@example.com")))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult unknownResult = mockMvc.perform(post(ACCOUNT_URL + "/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotPasswordJson("nobody-here@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(existingResult.getResponse().getStatus(), unknownResult.getResponse().getStatus());
        JsonNode existingBody = objectMapper.readTree(existingResult.getResponse().getContentAsString());
        JsonNode unknownBody = objectMapper.readTree(unknownResult.getResponse().getContentAsString());
        assertEquals(existingBody.get("message"), unknownBody.get("message"));
    }

    @Test
    void forgotPassword_shouldReturn400_whenEmailMalformed() throws Exception {
        mockMvc.perform(post(ACCOUNT_URL + "/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotPasswordJson("not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // --- POST /reset-password ---

    @Test
    void resetPassword_shouldReturn200AndAllowLoginWithNewPassword_whenTokenValid() throws Exception {
        User user = persistUser("reset-password-success@example.com", RoleName.USER);
        String rawToken = "known-raw-reset-token-success";
        passwordResetTokenRepository.save(new PasswordResetToken(
                user, secureTokenService.hashToken(rawToken), Instant.now().plus(Duration.ofMinutes(30))));
        String newPassword = "NewStr0ng!Pass1";

        mockMvc.perform(post(ACCOUNT_URL + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPasswordJson(rawToken, newPassword, newPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), newPassword)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_shouldReturn401_whenTokenUnknown() throws Exception {
        mockMvc.perform(post(ACCOUNT_URL + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPasswordJson("never-issued-reset-token", "NewStr0ng!Pass1", "NewStr0ng!Pass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void resetPassword_shouldReturn401_whenTokenExpired() throws Exception {
        User user = persistUser("reset-password-expired@example.com", RoleName.USER);
        String rawToken = "known-raw-reset-token-expired";
        passwordResetTokenRepository.save(new PasswordResetToken(
                user, secureTokenService.hashToken(rawToken), Instant.now().minusSeconds(1)));

        mockMvc.perform(post(ACCOUNT_URL + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPasswordJson(rawToken, "NewStr0ng!Pass1", "NewStr0ng!Pass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void resetPassword_shouldReturn400_whenConfirmationDoesNotMatch() throws Exception {
        User user = persistUser("reset-password-mismatch@example.com", RoleName.USER);
        String rawToken = "known-raw-reset-token-mismatch";
        passwordResetTokenRepository.save(new PasswordResetToken(
                user, secureTokenService.hashToken(rawToken), Instant.now().plus(Duration.ofMinutes(30))));

        mockMvc.perform(post(ACCOUNT_URL + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPasswordJson(rawToken, "NewStr0ng!Pass1", "Mismatch1!Aa")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    // --- POST /verify-email ---

    @Test
    void verifyEmail_shouldReturn200AndMarkUserVerified_whenTokenValid() throws Exception {
        User user = persistUser("verify-email-success@example.com", RoleName.USER);
        String rawToken = "known-raw-verification-token-success";
        emailVerificationTokenRepository.save(new EmailVerificationToken(
                user, secureTokenService.hashToken(rawToken), Instant.now().plus(Duration.ofHours(24))));

        mockMvc.perform(post(ACCOUNT_URL + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyEmailJson(rawToken)))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(reloaded.isEmailVerified());
    }

    @Test
    void verifyEmail_shouldReturn401_whenTokenUnknown() throws Exception {
        mockMvc.perform(post(ACCOUNT_URL + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyEmailJson("never-issued-verification-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void verifyEmail_shouldReturn401_whenTokenExpired() throws Exception {
        User user = persistUser("verify-email-expired@example.com", RoleName.USER);
        String rawToken = "known-raw-verification-token-expired";
        emailVerificationTokenRepository.save(new EmailVerificationToken(
                user, secureTokenService.hashToken(rawToken), Instant.now().minusSeconds(1)));

        mockMvc.perform(post(ACCOUNT_URL + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyEmailJson(rawToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    // --- POST /resend-verification ---

    @Test
    void resendVerification_shouldReturn200_whenAuthenticated() throws Exception {
        User user = persistUser("resend-verification-success@example.com", RoleName.USER);
        Cookie accessCookie = login(user.getEmail()).getResponse().getCookie(SecurityConstants.ACCESS_TOKEN_COOKIE);

        mockMvc.perform(post(ACCOUNT_URL + "/resend-verification").cookie(accessCookie))
                .andExpect(status().isOk());
    }

    @Test
    void resendVerification_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post(ACCOUNT_URL + "/resend-verification"))
                .andExpect(status().isUnauthorized());
    }

    // --- PUT /{id}/activate ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateUser_shouldReturn200_whenCalledByAdmin() throws Exception {
        User user = persistUser("activate-target@example.com", RoleName.USER);
        user.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(user);

        mockMvc.perform(put(ACCOUNT_URL + "/{id}/activate", user.getId()))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.ACTIVE, reloaded.getStatus());
    }

    @Test
    @WithMockUser(roles = "USER")
    void activateUser_shouldReturn403_whenCalledByNormalUser() throws Exception {
        User user = persistUser("activate-forbidden-target@example.com", RoleName.USER);

        mockMvc.perform(put(ACCOUNT_URL + "/{id}/activate", user.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void activateUser_shouldReturn401_whenUnauthenticated() throws Exception {
        User user = persistUser("activate-unauth-target@example.com", RoleName.USER);

        mockMvc.perform(put(ACCOUNT_URL + "/{id}/activate", user.getId()))
                .andExpect(status().isUnauthorized());
    }

    // --- PUT /{id}/deactivate ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateUser_shouldReturn200_whenCalledByAdmin() throws Exception {
        User user = persistUser("deactivate-target@example.com", RoleName.USER);

        mockMvc.perform(put(ACCOUNT_URL + "/{id}/deactivate", user.getId()))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.DEACTIVATED, reloaded.getStatus());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deactivateUser_shouldReturn403_whenCalledByNormalUser() throws Exception {
        User user = persistUser("deactivate-forbidden-target@example.com", RoleName.USER);

        mockMvc.perform(put(ACCOUNT_URL + "/{id}/deactivate", user.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void deactivateUser_shouldReturn401_whenUnauthenticated() throws Exception {
        User user = persistUser("deactivate-unauth-target@example.com", RoleName.USER);

        mockMvc.perform(put(ACCOUNT_URL + "/{id}/deactivate", user.getId()))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private User persistUser(String email, RoleName roleName) {
        var role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User("Test User", email, passwordEncoder.encode(VALID_PASSWORD), role);
        user.setStatus(UserStatus.ACTIVE);
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

    private String updateProfileJson(String name) {
        return """
                {"name": "%s"}
                """.formatted(name);
    }

    private String changePasswordJson(String currentPassword, String newPassword, String confirmPassword) {
        return """
                {"currentPassword": "%s", "newPassword": "%s", "confirmPassword": "%s"}
                """.formatted(currentPassword, newPassword, confirmPassword);
    }

    private String forgotPasswordJson(String email) {
        return """
                {"email": "%s"}
                """.formatted(email);
    }

    private String resetPasswordJson(String token, String newPassword, String confirmPassword) {
        return """
                {"token": "%s", "newPassword": "%s", "confirmPassword": "%s"}
                """.formatted(token, newPassword, confirmPassword);
    }

    private String verifyEmailJson(String token) {
        return """
                {"token": "%s"}
                """.formatted(token);
    }
}
