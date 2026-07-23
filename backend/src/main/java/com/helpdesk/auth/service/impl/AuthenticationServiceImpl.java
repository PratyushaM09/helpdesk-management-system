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

    public AuthenticationServiceImpl(UserRepository userRepository,
                                      RefreshTokenRepository refreshTokenRepository,
                                      AuthenticationManager authenticationManager,
                                      JwtService jwtService,
                                      CookieService cookieService,
                                      JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.cookieService = cookieService;
        this.jwtProperties = jwtProperties;
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

    // --- login helpers ---

    private void rejectIfLocked(User user, String email) {
        if (user == null) {
            return;
        }
        Instant now = Instant.now();
        if (user.getStatus() == UserStatus.LOCKED && !user.isLocked(now)) {
            // Window expired - auto-clear the status, but failedAttempts is
            // deliberately left untouched (SDR-005: resets only on success).
            user.setStatus(UserStatus.VERIFIED);
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

    /** No-op for a nonexistent account — nothing to persist, and no side effect that could reveal its non-existence. */
    private void recordFailedAttempt(User user, String email) {
        auditLog.warn("Login failed: email={}", email);
        if (user == null) {
            return;
        }
        user.recordFailedLoginAttempt();
        if (user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            user.lockUntil(Instant.now().plus(LOCKOUT_DURATION));
            user.setStatus(UserStatus.LOCKED);
            auditLog.warn("Account locked after {} failed attempts: userId={}", MAX_FAILED_ATTEMPTS, user.getId());
        }
        userRepository.save(user);
    }

    // --- refresh helpers ---

    private RefreshToken resolveRefreshTokenOrThrow(String rawRefreshToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));
    }

    private void revokeEntireFamily(RefreshToken replayedToken) {
        Instant now = Instant.now();
        List<RefreshToken> family = refreshTokenRepository.findByFamilyId(replayedToken.getFamilyId());
        family.stream().filter(token -> !token.isRevoked()).forEach(token -> token.revoke(now));
        refreshTokenRepository.saveAll(family);
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

        List<ResponseCookie> cookies = includeCsrfCookie
                ? List.of(
                        cookieService.createAccessTokenCookie(accessToken),
                        cookieService.createRefreshTokenCookie(generated.rawValue()),
                        cookieService.createCsrfTokenCookie(generateRawToken()))
                : List.of(
                        cookieService.createAccessTokenCookie(accessToken),
                        cookieService.createRefreshTokenCookie(generated.rawValue()));

        LoginResponse response = new LoginResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().getName());
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
