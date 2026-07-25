package com.helpdesk.auth.service.impl;

import com.helpdesk.auth.dto.request.LoginRequest;
import com.helpdesk.auth.entity.RefreshToken;
import com.helpdesk.auth.repository.RefreshTokenRepository;
import com.helpdesk.auth.service.AuthenticationResult;
import com.helpdesk.exception.LockedException;
import com.helpdesk.exception.UnauthorizedException;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.security.CookieService;
import com.helpdesk.security.JwtProperties;
import com.helpdesk.security.JwtService;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — every collaborator is mocked, matching
 * {@code UserServiceImplTest}'s convention; {@link JwtProperties} is a real
 * instance (a record, cheaper to construct than mock). {@code User}/
 * {@code Role}/{@code RefreshToken} are real domain objects throughout — the
 * point of these tests is verifying how {@code AuthenticationServiceImpl}
 * mutates and orchestrates them, so mocking the entities themselves would
 * defeat the purpose.
 */
class AuthenticationServiceImplTest {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AuthenticationManager authenticationManager;
    private JwtService jwtService;
    private CookieService cookieService;
    private AuthSecurityEventRecorder securityEventRecorder;
    private AuthenticationServiceImpl authenticationService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        authenticationManager = mock(AuthenticationManager.class);
        jwtService = mock(JwtService.class);
        cookieService = mock(CookieService.class);
        securityEventRecorder = mock(AuthSecurityEventRecorder.class);
        JwtProperties jwtProperties = new JwtProperties("HS512", "irrelevant-for-this-test", null, null,
                Duration.ofMinutes(15), Duration.ofDays(7), "test-issuer");
        authenticationService = new AuthenticationServiceImpl(
                userRepository, refreshTokenRepository, authenticationManager, jwtService, cookieService, jwtProperties,
                securityEventRecorder);
    }

    // --- login ---

    @Test
    void login_shouldReturnResultAndResetFailedAttempts_whenCredentialsValid() {
        User user = aUser(RoleName.USER);
        user.recordFailedLoginAttempt();
        LoginRequest request = new LoginRequest("ada@example.com", "Str0ng!Passw0rd");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(jwtService.generateAccessToken(any())).thenReturn("access-token-value");
        ResponseCookie accessCookie = ResponseCookie.from("access_token", "a").build();
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "b").build();
        ResponseCookie csrfCookie = ResponseCookie.from("csrf_token", "c").build();
        when(cookieService.createAccessTokenCookie("access-token-value")).thenReturn(accessCookie);
        when(cookieService.createRefreshTokenCookie(any())).thenReturn(refreshCookie);
        when(cookieService.createCsrfTokenCookie(any())).thenReturn(csrfCookie);

        AuthenticationResult result = authenticationService.login(request);

        assertEquals(user.getId(), result.response().id());
        assertEquals(user.getName(), result.response().name());
        assertEquals(user.getEmail(), result.response().email());
        assertEquals(RoleName.USER, result.response().role());
        assertEquals(List.of(accessCookie, refreshCookie, csrfCookie), result.cookies());
        assertEquals(0, user.getFailedAttempts());
        verify(userRepository).save(user);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_shouldThrowUnauthorized_andNeverPersist_whenEmailUnknown() {
        LoginRequest request = new LoginRequest("ghost@example.com", "whatever");
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(UnauthorizedException.class, () -> authenticationService.login(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository, jwtService, cookieService);
    }

    /**
     * Whether this specific attempt crosses the lockout threshold is
     * {@link AuthSecurityEventRecorder}'s decision, not this class's - see
     * {@code AuthSecurityEventRecorderTest} for that behavior. This class's
     * own contract is just "delegate to it, in its own transaction, with the
     * right arguments" - {@link AuthSecurityEventRecorder}'s Javadoc explains
     * why that delegation (rather than persisting here directly) is required.
     */
    @Test
    void login_shouldDelegateFailedAttemptRecording_andThrowUnauthorized_whenPasswordWrong() {
        User user = aUser(RoleName.USER);
        LoginRequest request = new LoginRequest("ada@example.com", "wrong-password");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(UnauthorizedException.class, () -> authenticationService.login(request));

        verify(securityEventRecorder).recordFailedAttempt(user.getId(), MAX_FAILED_ATTEMPTS, LOCKOUT_DURATION);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository, jwtService, cookieService);
    }

    @Test
    void login_shouldThrowLocked_andNeverCallAuthenticationManager_whenCurrentlyLocked() {
        User user = aUser(RoleName.USER);
        user.lockUntil(Instant.now().plus(Duration.ofMinutes(10)));
        user.setStatus(UserStatus.LOCKED);
        LoginRequest request = new LoginRequest("ada@example.com", "Str0ng!Passw0rd");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        assertThrows(LockedException.class, () -> authenticationService.login(request));

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_shouldAutoClearExpiredLockStatus_andStillDelegateFailedAttemptRecording_whenWindowExpiredAndLoginStillFails() {
        User user = aUser(RoleName.USER);
        for (int i = 0; i < 5; i++) {
            user.recordFailedLoginAttempt();
        }
        user.lockUntil(Instant.now().minus(Duration.ofMinutes(1)));
        user.setStatus(UserStatus.LOCKED);
        LoginRequest request = new LoginRequest("ada@example.com", "still-wrong");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(UnauthorizedException.class, () -> authenticationService.login(request));

        verify(authenticationManager).authenticate(any());
        verify(securityEventRecorder).recordFailedAttempt(user.getId(), MAX_FAILED_ATTEMPTS, LOCKOUT_DURATION);
    }

    // --- refresh ---

    @Test
    void refresh_shouldRotateTokenWithSameFamilyId_whenValid() {
        User user = aUser(RoleName.USER);
        RefreshToken existing = new RefreshToken(user, "irrelevant-hash", Instant.now().plus(Duration.ofDays(1)), "family-123");
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));
        when(jwtService.generateAccessToken(any())).thenReturn("new-access-token");
        when(cookieService.createAccessTokenCookie(any())).thenReturn(ResponseCookie.from("access_token", "x").build());
        when(cookieService.createRefreshTokenCookie(any())).thenReturn(ResponseCookie.from("refresh_token", "y").build());

        AuthenticationResult result = authenticationService.refresh("raw-refresh-token");

        assertEquals(2, result.cookies().size());
        verify(cookieService, never()).createCsrfTokenCookie(any());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        RefreshToken newToken = captor.getAllValues().stream().filter(token -> token != existing).findFirst().orElseThrow();
        assertEquals("family-123", newToken.getFamilyId());
        // isReplaced() alone is what rejects a future replay of this token
        // (refresh()'s own first check) - rotation deliberately does not
        // also call revoke(); that flag is reserved for logout/reuse-kill.
        assertTrue(existing.isReplaced());
        assertEquals(newToken, existing.getReplacedBy());
    }

    @Test
    void refresh_shouldThrowUnauthorized_whenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authenticationService.refresh("unknown-token"));

        verifyNoInteractions(jwtService, cookieService);
    }

    @Test
    void refresh_shouldThrowUnauthorized_whenTokenExpired() {
        User user = aUser(RoleName.USER);
        RefreshToken expired = new RefreshToken(user, "hash", Instant.now().minus(Duration.ofSeconds(1)), "family-1");
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThrows(UnauthorizedException.class, () -> authenticationService.refresh("raw"));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_shouldThrowUnauthorized_whenTokenAlreadyRevoked() {
        User user = aUser(RoleName.USER);
        RefreshToken revoked = new RefreshToken(user, "hash", Instant.now().plus(Duration.ofDays(1)), "family-1");
        revoked.revoke(Instant.now());
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThrows(UnauthorizedException.class, () -> authenticationService.refresh("raw"));

        verify(refreshTokenRepository, never()).save(any());
    }

    /**
     * The actual family-wide revocation is {@link AuthSecurityEventRecorder}'s
     * job now (its own transaction, for the reason its Javadoc explains) -
     * this class's contract is just delegating the family id. See
     * {@code AuthSecurityEventRecorderTest} for the revocation behavior itself.
     */
    @Test
    void refresh_shouldDelegateFamilyRevocationAndThrowUnauthorized_whenTokenAlreadyReplaced() {
        User user = aUser(RoleName.USER);
        RefreshToken replayed = new RefreshToken(user, "hash-a", Instant.now().plus(Duration.ofDays(1)), "family-1");
        RefreshToken successor = new RefreshToken(user, "hash-b", Instant.now().plus(Duration.ofDays(1)), "family-1");
        replayed.markReplacedBy(successor);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(replayed));

        assertThrows(UnauthorizedException.class, () -> authenticationService.refresh("raw"));

        verify(securityEventRecorder).revokeFamily("family-1");
        verify(refreshTokenRepository, never()).saveAll(anyList());
        verifyNoInteractions(jwtService, cookieService);
    }

    // --- logout ---

    @Test
    void logout_shouldRevokeTokenAndReturnClearedCookies_whenTokenFound() {
        User user = aUser(RoleName.USER);
        RefreshToken token = new RefreshToken(user, "hash", Instant.now().plus(Duration.ofDays(1)), "family-1");
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        List<ResponseCookie> cleared = List.of(ResponseCookie.from("access_token", "").build());
        when(cookieService.clearAllCookies()).thenReturn(cleared);

        List<ResponseCookie> result = authenticationService.logout("raw-token");

        assertEquals(cleared, result);
        assertTrue(token.isRevoked());
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logout_shouldReturnClearedCookies_andNeverSave_whenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        List<ResponseCookie> cleared = List.of(ResponseCookie.from("access_token", "").build());
        when(cookieService.clearAllCookies()).thenReturn(cleared);

        List<ResponseCookie> result = authenticationService.logout("unknown-token");

        assertEquals(cleared, result);
        verify(refreshTokenRepository, never()).save(any());
    }

    private User aUser(RoleName roleName) {
        Role role = new Role(roleName, roleName.name());
        return new User("Ada Lovelace", "ada@example.com", "hashed-password", role);
    }
}
