package com.helpdesk.account.service.impl;

import com.helpdesk.account.dto.request.ChangePasswordRequest;
import com.helpdesk.account.dto.request.ForgotPasswordRequest;
import com.helpdesk.account.dto.request.ResetPasswordRequest;
import com.helpdesk.account.dto.request.UpdateProfileRequest;
import com.helpdesk.account.dto.request.VerifyEmailRequest;
import com.helpdesk.account.dto.response.AccountProfileResponse;
import com.helpdesk.account.entity.EmailVerificationToken;
import com.helpdesk.account.entity.PasswordResetToken;
import com.helpdesk.account.mapper.AccountMapper;
import com.helpdesk.account.repository.EmailVerificationTokenRepository;
import com.helpdesk.account.repository.PasswordResetTokenRepository;
import com.helpdesk.account.service.SecureTokenService;
import com.helpdesk.auth.service.AuthenticationService;
import com.helpdesk.exception.BadRequestException;
import com.helpdesk.exception.InvalidTokenException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.notification.service.NotificationService;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.security.UserPrincipal;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — every collaborator is mocked, matching
 * {@code RoleServiceImplTest}/{@code UserServiceImplTest}'s convention; no
 * Spring context, no database. {@code SecurityContextHolder} is a
 * ThreadLocal populated manually via {@link #authenticateAs(Long)} for
 * methods that resolve the caller from it, and cleared before/after every
 * test (same convention {@code JwtAuthenticationFilterTest} documents) to
 * prevent cross-test pollution.
 */
class AccountServiceImplTest {

    private UserRepository userRepository;
    private AccountMapper accountMapper;
    private PasswordEncoder passwordEncoder;
    private AuthenticationService authenticationService;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    private NotificationService notificationService;
    private SecureTokenService secureTokenService;
    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        accountMapper = mock(AccountMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationService = mock(AuthenticationService.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        emailVerificationTokenRepository = mock(EmailVerificationTokenRepository.class);
        notificationService = mock(NotificationService.class);
        secureTokenService = mock(SecureTokenService.class);
        accountService = new AccountServiceImpl(userRepository, accountMapper, passwordEncoder, authenticationService,
                passwordResetTokenRepository, emailVerificationTokenRepository, notificationService, secureTokenService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- getCurrentUserProfile ---

    @Test
    void getCurrentUserProfile_shouldReturnMappedProfile_whenUserExists() {
        authenticateAs(1L);
        User user = aUser(aRole(RoleName.USER));
        AccountProfileResponse expected = aProfileResponse(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountMapper.toAccountProfile(user)).thenReturn(expected);

        AccountProfileResponse result = accountService.getCurrentUserProfile();

        assertEquals(expected, result);
    }

    @Test
    void getCurrentUserProfile_shouldThrowNotFound_whenAuthenticatedUserNoLongerExists() {
        authenticateAs(404L);
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.getCurrentUserProfile());

        verifyNoInteractions(accountMapper);
    }

    // --- updateProfile ---

    @Test
    void updateProfile_shouldUpdateNameAndReturnMappedProfile_whenUserExists() {
        authenticateAs(1L);
        UpdateProfileRequest request = new UpdateProfileRequest("New Name");
        User user = aUser(aRole(RoleName.USER));
        AccountProfileResponse expected = aProfileResponse(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(accountMapper.toAccountProfile(user)).thenReturn(expected);

        AccountProfileResponse result = accountService.updateProfile(request);

        assertEquals(expected, result);
        assertEquals("New Name", user.getName());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_shouldThrowNotFound_whenAuthenticatedUserNoLongerExists() {
        authenticateAs(404L);
        UpdateProfileRequest request = new UpdateProfileRequest("New Name");
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.updateProfile(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(accountMapper);
    }

    // --- changePassword ---

    @Test
    void changePassword_shouldEncodePasswordIncrementTokenVersionAndRevokeTokens_whenCurrentPasswordCorrect() {
        authenticateAs(1L);
        ChangePasswordRequest request = new ChangePasswordRequest("OldP@ssw0rd1", "NewP@ssw0rd1", "NewP@ssw0rd1");
        User user = aUser(aRole(RoleName.USER));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldP@ssw0rd1", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewP@ssw0rd1")).thenReturn("new-hashed-password");
        when(userRepository.save(user)).thenReturn(user);

        accountService.changePassword(request);

        assertEquals("new-hashed-password", user.getPasswordHash());
        assertEquals(1, user.getTokenVersion());
        verify(authenticationService).revokeAllRefreshTokensForUser(user);
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_shouldThrowBadRequest_whenCurrentPasswordIncorrect() {
        authenticateAs(1L);
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword1", "NewP@ssw0rd1", "NewP@ssw0rd1");
        User user = aUser(aRole(RoleName.USER));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword1", user.getPasswordHash())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> accountService.changePassword(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authenticationService);
    }

    @Test
    void changePassword_shouldThrowBadRequest_whenConfirmationDoesNotMatch() {
        authenticateAs(1L);
        ChangePasswordRequest request = new ChangePasswordRequest("OldP@ssw0rd1", "NewP@ssw0rd1", "Mismatch1!");
        User user = aUser(aRole(RoleName.USER));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldP@ssw0rd1", user.getPasswordHash())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> accountService.changePassword(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authenticationService);
    }

    @Test
    void changePassword_shouldThrowBadRequest_whenNewPasswordEqualsCurrentPassword() {
        authenticateAs(1L);
        ChangePasswordRequest request = new ChangePasswordRequest("SameP@ssw0rd1", "SameP@ssw0rd1", "SameP@ssw0rd1");
        User user = aUser(aRole(RoleName.USER));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SameP@ssw0rd1", user.getPasswordHash())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> accountService.changePassword(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authenticationService);
    }

    @Test
    void changePassword_shouldThrowNotFound_whenAuthenticatedUserNoLongerExists() {
        authenticateAs(404L);
        ChangePasswordRequest request = new ChangePasswordRequest("OldP@ssw0rd1", "NewP@ssw0rd1", "NewP@ssw0rd1");
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.changePassword(request));

        verifyNoInteractions(passwordEncoder, authenticationService);
    }

    // --- forgotPassword ---

    @Test
    void forgotPassword_shouldPersistHashedTokenWithCorrectExpiryAndNotify_whenEmailExists() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("ada@example.com");
        User user = aUser(aRole(RoleName.USER));
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(secureTokenService.generateToken()).thenReturn("raw-token");
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");

        accountService.forgotPassword(request);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        PasswordResetToken savedToken = captor.getValue();
        assertEquals("hashed-token", savedToken.getTokenHash());
        assertEquals(user, savedToken.getUser());
        assertExpiresApproximately(savedToken.getExpiresAt(), Duration.ofMinutes(30));
        verify(notificationService).sendPasswordResetEmail("ada@example.com", "raw-token");
    }

    @Test
    void forgotPassword_shouldDoNothing_whenEmailDoesNotExist() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("ghost@example.com");
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> accountService.forgotPassword(request));

        verifyNoInteractions(passwordResetTokenRepository, notificationService, secureTokenService);
    }

    // --- resetPassword ---

    @Test
    void resetPassword_shouldChangePasswordIncrementTokenVersionRevokeTokensAndMarkTokenUsed_whenTokenValid() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "NewP@ssw0rd1", "NewP@ssw0rd1");
        User user = aUser(aRole(RoleName.USER));
        PasswordResetToken token = aPasswordResetToken(user, Instant.now().plus(Duration.ofMinutes(20)));
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("NewP@ssw0rd1", user.getPasswordHash())).thenReturn(false);
        when(passwordEncoder.encode("NewP@ssw0rd1")).thenReturn("new-hashed-password");
        when(userRepository.save(user)).thenReturn(user);
        when(passwordResetTokenRepository.save(token)).thenReturn(token);

        accountService.resetPassword(request);

        assertEquals("new-hashed-password", user.getPasswordHash());
        assertEquals(1, user.getTokenVersion());
        assertTrue(token.isUsed());
        verify(authenticationService).revokeAllRefreshTokensForUser(user);
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void resetPassword_shouldThrowInvalidToken_whenTokenDoesNotExist() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "NewP@ssw0rd1", "NewP@ssw0rd1");
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> accountService.resetPassword(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authenticationService);
    }

    @Test
    void resetPassword_shouldThrowInvalidToken_whenTokenExpired() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "NewP@ssw0rd1", "NewP@ssw0rd1");
        User user = aUser(aRole(RoleName.USER));
        PasswordResetToken token = aPasswordResetToken(user, Instant.now().minusSeconds(1));
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> accountService.resetPassword(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authenticationService);
    }

    @Test
    void resetPassword_shouldThrowInvalidToken_whenTokenAlreadyUsed() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "NewP@ssw0rd1", "NewP@ssw0rd1");
        User user = aUser(aRole(RoleName.USER));
        PasswordResetToken token = aPasswordResetToken(user, Instant.now().plus(Duration.ofMinutes(10)));
        token.markUsed(Instant.now());
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> accountService.resetPassword(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authenticationService);
    }

    @Test
    void resetPassword_shouldThrowBadRequest_whenConfirmationDoesNotMatch() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "NewP@ssw0rd1", "Mismatch1!");
        User user = aUser(aRole(RoleName.USER));
        PasswordResetToken token = aPasswordResetToken(user, Instant.now().plus(Duration.ofMinutes(10)));
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertThrows(BadRequestException.class, () -> accountService.resetPassword(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authenticationService);
    }

    @Test
    void resetPassword_shouldThrowBadRequest_whenNewPasswordEqualsExistingPassword() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "SameP@ssw0rd1", "SameP@ssw0rd1");
        User user = aUser(aRole(RoleName.USER));
        PasswordResetToken token = aPasswordResetToken(user, Instant.now().plus(Duration.ofMinutes(10)));
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("SameP@ssw0rd1", user.getPasswordHash())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> accountService.resetPassword(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authenticationService);
    }

    // --- verifyEmail ---

    @Test
    void verifyEmail_shouldMarkUserVerifiedAndTokenUsed_whenTokenValid() {
        VerifyEmailRequest request = new VerifyEmailRequest("raw-token");
        User user = aUser(aRole(RoleName.USER));
        EmailVerificationToken token = anEmailVerificationToken(user, Instant.now().plus(Duration.ofHours(1)));
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(emailVerificationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        accountService.verifyEmail(request);

        assertTrue(user.isEmailVerified());
        assertTrue(token.isUsed());
        verify(userRepository).save(user);
        verify(emailVerificationTokenRepository).save(token);
    }

    @Test
    void verifyEmail_shouldThrowInvalidToken_whenTokenDoesNotExist() {
        VerifyEmailRequest request = new VerifyEmailRequest("raw-token");
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(emailVerificationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> accountService.verifyEmail(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_shouldThrowInvalidToken_whenTokenExpired() {
        VerifyEmailRequest request = new VerifyEmailRequest("raw-token");
        User user = aUser(aRole(RoleName.USER));
        EmailVerificationToken token = anEmailVerificationToken(user, Instant.now().minusSeconds(1));
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(emailVerificationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> accountService.verifyEmail(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_shouldThrowInvalidToken_whenTokenAlreadyUsed() {
        VerifyEmailRequest request = new VerifyEmailRequest("raw-token");
        User user = aUser(aRole(RoleName.USER));
        EmailVerificationToken token = anEmailVerificationToken(user, Instant.now().plus(Duration.ofHours(1)));
        token.markUsed(Instant.now());
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(emailVerificationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> accountService.verifyEmail(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_shouldSucceedAndPreserveOriginalTimestamp_whenUserAlreadyVerified() {
        VerifyEmailRequest request = new VerifyEmailRequest("raw-token");
        User user = aUser(aRole(RoleName.USER));
        Instant originalVerifiedAt = Instant.now().minus(Duration.ofDays(1));
        user.markEmailVerified(originalVerifiedAt);
        EmailVerificationToken token = anEmailVerificationToken(user, Instant.now().plus(Duration.ofHours(1)));
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(emailVerificationTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        assertDoesNotThrow(() -> accountService.verifyEmail(request));

        assertTrue(user.isEmailVerified());
        assertEquals(originalVerifiedAt, user.getEmailVerifiedAt());
        assertTrue(token.isUsed());
        verify(userRepository).save(user);
        verify(emailVerificationTokenRepository).save(token);
    }

    // --- resendVerification ---

    @Test
    void resendVerification_shouldPersistHashedTokenWithCorrectExpiryAndNotify_whenUserNotVerified() {
        authenticateAs(1L);
        User user = aUser(aRole(RoleName.USER));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(secureTokenService.generateToken()).thenReturn("raw-token");
        when(secureTokenService.hashToken("raw-token")).thenReturn("hashed-token");

        accountService.resendVerification();

        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(captor.capture());
        EmailVerificationToken savedToken = captor.getValue();
        assertEquals("hashed-token", savedToken.getTokenHash());
        assertEquals(user, savedToken.getUser());
        assertExpiresApproximately(savedToken.getExpiresAt(), Duration.ofHours(24));
        verify(notificationService).sendEmailVerificationEmail("ada@example.com", "raw-token");
    }

    @Test
    void resendVerification_shouldDoNothing_whenUserAlreadyVerified() {
        authenticateAs(1L);
        User user = aUser(aRole(RoleName.USER));
        user.markEmailVerified(Instant.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        accountService.resendVerification();

        verifyNoInteractions(emailVerificationTokenRepository, notificationService, secureTokenService);
    }

    // --- activateUser ---

    @Test
    void activateUser_shouldSetStatusActive_whenUserIsDeactivated() {
        User user = aUser(aRole(RoleName.USER));
        user.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        accountService.activateUser(1L);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void activateUser_shouldDoNothing_whenAlreadyActive() {
        User user = aUser(aRole(RoleName.USER));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        accountService.activateUser(1L);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void activateUser_shouldThrowNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.activateUser(404L));

        verify(userRepository, never()).save(any());
    }

    // --- deactivateUser ---

    @Test
    void deactivateUser_shouldSetStatusDeactivatedIncrementTokenVersionAndRevokeTokens_whenUserIsActive() {
        User user = aUser(aRole(RoleName.USER));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        accountService.deactivateUser(1L);

        assertEquals(UserStatus.DEACTIVATED, user.getStatus());
        assertEquals(1, user.getTokenVersion());
        verify(authenticationService).revokeAllRefreshTokensForUser(user);
        verify(userRepository).save(user);
    }

    @Test
    void deactivateUser_shouldDoNothing_whenAlreadyDeactivated() {
        User user = aUser(aRole(RoleName.USER));
        user.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        accountService.deactivateUser(1L);

        assertEquals(0, user.getTokenVersion());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(authenticationService);
    }

    @Test
    void deactivateUser_shouldThrowNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.deactivateUser(404L));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authenticationService);
    }

    // --- fixtures ---

    private void authenticateAs(Long userId) {
        UserPrincipal principal =
                new UserPrincipal(userId, "ada@example.com", "hashed-password", RoleName.USER, UserStatus.ACTIVE, 0);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Role aRole(RoleName name) {
        return new Role(name, name.name());
    }

    private User aUser(Role role) {
        return new User("Ada Lovelace", "ada@example.com", "hashed-password", role);
    }

    private AccountProfileResponse aProfileResponse(Long id) {
        return new AccountProfileResponse(id, "Ada Lovelace", "ada@example.com", RoleName.USER, UserStatus.ACTIVE,
                false, Instant.now());
    }

    private PasswordResetToken aPasswordResetToken(User user, Instant expiresAt) {
        return new PasswordResetToken(user, "existing-hashed-token", expiresAt);
    }

    private EmailVerificationToken anEmailVerificationToken(User user, Instant expiresAt) {
        return new EmailVerificationToken(user, "existing-hashed-token", expiresAt);
    }

    /** Asserts an expiry is within a 5-second tolerance of "now + ttl" — there is no injectable {@code Clock} in this codebase. */
    private void assertExpiresApproximately(Instant actual, Duration ttl) {
        Instant expected = Instant.now().plus(ttl);
        assertTrue(actual.isAfter(expected.minusSeconds(5)), "expiresAt too early: " + actual);
        assertTrue(actual.isBefore(expected.plusSeconds(5)), "expiresAt too late: " + actual);
    }
}
