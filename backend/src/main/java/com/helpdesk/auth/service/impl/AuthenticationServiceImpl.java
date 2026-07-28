package com.helpdesk.auth.service.impl;

import com.helpdesk.auth.dto.request.LoginRequest;
import com.helpdesk.auth.dto.response.LoginResponse;
import com.helpdesk.auth.entity.RefreshToken;
import com.helpdesk.auth.repository.RefreshTokenRepository;
import com.helpdesk.auth.service.AuthenticationResult;
import com.helpdesk.auth.service.AuthenticationService;
import com.helpdesk.exception.LockedException;
import com.helpdesk.exception.UnauthorizedException;
import com.helpdesk.security.CookieService;
import com.helpdesk.security.JwtProperties;
import com.helpdesk.security.JwtService;
import com.helpdesk.security.UserPrincipal;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The Authentication module's orchestration layer (07-Security-Architecture.md
 * §3) — the only place login/refresh/logout business rules are decided.
 * {@link JwtService} only mints/verifies tokens, {@link CookieService} only
 * builds cookies, the repositories only persist — none of them know *why*
 * they're being called; this class is the one that does. See the
 * conversation's login-lifecycle walkthrough for the full step-by-step
 * narrative this method set implements.
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    /** Distinct from the general application log (02-Architecture.md §13) — never mixed with it. */
    private static final Logger auditLog = LoggerFactory.getLogger("com.helpdesk.security.audit");

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final int RAW_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CookieService cookieService;
    private final JwtProperties jwtProperties;
    private final AuthSecurityEventRecorder securityEventRecorder;

    public AuthenticationServiceImpl(UserRepository userRepository,
                                      RefreshTokenRepository refreshTokenRepository,
                                      AuthenticationManager authenticationManager,
                                      JwtService jwtService,
                                      CookieService cookieService,
                                      JwtProperties jwtProperties,
                                      AuthSecurityEventRecorder securityEventRecorder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.cookieService = cookieService;
        this.jwtProperties = jwtProperties;
        this.securityEventRecorder = securityEventRecorder;
    }

    @Override
    @Transactional
    public AuthenticationResult login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        rejectIfLocked(user, request.email());
        authenticateCredentials(request.email(), request.password(), user);

        user.resetFailedAttempts();
        userRepository.save(user);
        auditLog.info("Login succeeded: userId={}", user.getId());

        GeneratedRefreshToken generated = createAndPersistRefreshToken(user, UUID.randomUUID().toString());
        return buildResult(user, generated, true);
    }

    @Override
    @Transactional
    public AuthenticationResult refresh(String rawRefreshToken) {
        RefreshToken existing = resolveRefreshTokenOrThrow(rawRefreshToken);
        Instant now = Instant.now();

        if (existing.isReplaced()) {
            revokeEntireFamily(existing);
            throw new UnauthorizedException("Invalid refresh token.");
        }
        if (existing.isRevoked() || existing.isExpired(now)) {
            throw new UnauthorizedException("Invalid refresh token.");
        }

        User user = existing.getUser();
        GeneratedRefreshToken generated = createAndPersistRefreshToken(user, existing.getFamilyId());
        existing.markReplacedBy(generated.entity());
        refreshTokenRepository.save(existing);
        auditLog.info("Refresh token rotated: userId={}", user.getId());

        return buildResult(user, generated, false);
    }

    @Override
    @Transactional
    public List<ResponseCookie> logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresentOrElse(
                token -> {
                    token.revoke(Instant.now());
                    refreshTokenRepository.save(token);
                    auditLog.info("Logout: userId={}", token.getUser().getId());
                },
                () -> auditLog.info("Logout called with no matching refresh token (already invalid or expired).")
        );
        return cookieService.clearAllCookies();
    }

    @Override
    public ResponseCookie issueCsrfCookie() {
        return cookieService.createCsrfTokenCookie(generateRawToken());
    }

    @Override
    @Transactional
    public void revokeAllRefreshTokensForUser(User user) {
        Instant now = Instant.now();
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(user.getId());
        tokens.stream().filter(token -> !token.isRevoked()).forEach(token -> token.revoke(now));
        refreshTokenRepository.saveAll(tokens);
        auditLog.info("All refresh tokens revoked for user: userId={}", user.getId());
    }

    // --- login helpers ---

    private void rejectIfLocked(User user, String email) {
        if (user == null) {
            return;
        }
        Instant now = Instant.now();
        if (user.getStatus() == UserStatus.LOCKED && !user.isLocked(now)) {
            // Window expired - auto-clear the status, but failedAttempts is
            // deliberately left untouched (SDR-005: resets only on success).
            user.setStatus(UserStatus.ACTIVE);
        }
        if (user.isLocked(now)) {
            auditLog.warn("Login rejected - account locked: email={}", email);
            throw new LockedException("Too many failed attempts. Please try again in a few minutes.");
        }
    }

    private void authenticateCredentials(String email, String rawPassword, User user) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, rawPassword));
        } catch (AuthenticationException e) {
            recordFailedAttempt(user, email);
            throw new UnauthorizedException("Invalid email or password.");
        }
    }

    /**
     * No-op for a nonexistent account — nothing to persist, and no side
     * effect that could reveal its non-existence. Delegates the actual
     * persistence to {@link AuthSecurityEventRecorder}, which runs in its
     * own transaction — {@code login()} always ends this path by throwing
     * {@link UnauthorizedException}, and since that's an unchecked exception,
     * Spring's default rollback rule would otherwise undo this write along
     * with the rest of {@code login()}'s transaction, silently disabling
     * account lockout. See that class's Javadoc for the full explanation.
     */
    private void recordFailedAttempt(User user, String email) {
        auditLog.warn("Login failed: email={}", email);
        if (user == null) {
            return;
        }
        boolean locked = securityEventRecorder.recordFailedAttempt(user.getId(), MAX_FAILED_ATTEMPTS, LOCKOUT_DURATION);
        if (locked) {
            auditLog.warn("Account locked after {} failed attempts: userId={}", MAX_FAILED_ATTEMPTS, user.getId());
        }
    }

    // --- refresh helpers ---

    private RefreshToken resolveRefreshTokenOrThrow(String rawRefreshToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));
    }

    /**
     * Same REQUIRES_NEW reasoning as {@link #recordFailedAttempt} — {@code
     * refresh()} always ends this path by throwing {@link UnauthorizedException},
     * which would otherwise roll this revocation back along with everything
     * else, silently disabling the stolen-refresh-token defense this method
     * exists to provide. See {@link AuthSecurityEventRecorder}'s Javadoc.
     */
    private void revokeEntireFamily(RefreshToken replayedToken) {
        securityEventRecorder.revokeFamily(replayedToken.getFamilyId());
        auditLog.warn("Refresh token reuse detected - entire token family revoked: userId={}, familyId={}",
                replayedToken.getUser().getId(), replayedToken.getFamilyId());
    }

    // --- shared session-issuance helpers ---

    private GeneratedRefreshToken createAndPersistRefreshToken(User user, String familyId) {
        String rawValue = generateRawToken();
        Instant expiresAt = Instant.now().plus(jwtProperties.refreshTokenTtl());
        RefreshToken entity = new RefreshToken(user, hash(rawValue), expiresAt, familyId);
        refreshTokenRepository.save(entity);
        return new GeneratedRefreshToken(entity, rawValue);
    }

    private AuthenticationResult buildResult(User user, GeneratedRefreshToken generated, boolean includeCsrfCookie) {
        String accessToken = jwtService.generateAccessToken(toPrincipal(user));

        List<ResponseCookie> cookies;
        String csrfToken;
        if (includeCsrfCookie) {
            ResponseCookie csrfCookie = cookieService.createCsrfTokenCookie(generateRawToken());
            csrfToken = csrfCookie.getValue();
            cookies = List.of(
                    cookieService.createAccessTokenCookie(accessToken),
                    cookieService.createRefreshTokenCookie(generated.rawValue()),
                    csrfCookie);
        } else {
            csrfToken = null;
            cookies = List.of(
                    cookieService.createAccessTokenCookie(accessToken),
                    cookieService.createRefreshTokenCookie(generated.rawValue()));
        }

        LoginResponse response = new LoginResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().getName(), csrfToken);
        return new AuthenticationResult(response, cookies);
    }

    private UserPrincipal toPrincipal(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().getName(),
                user.getStatus(),
                user.getTokenVersion()
        );
    }

    // --- opaque token generation ---

    private static String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }

    private record GeneratedRefreshToken(RefreshToken entity, String rawValue) {
    }
}
