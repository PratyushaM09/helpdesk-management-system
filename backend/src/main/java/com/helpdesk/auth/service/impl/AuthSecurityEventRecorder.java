package com.helpdesk.auth.service.impl;

import com.helpdesk.auth.entity.RefreshToken;
import com.helpdesk.auth.repository.RefreshTokenRepository;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Persists the two security bookkeeping writes {@link AuthenticationServiceImpl}
 * must keep even though the method that triggers them always ends by throwing:
 * a failed-login-attempt count (possibly crossing into a lockout) and a
 * refresh-token family revocation on replay detection.
 * <p>
 * Both call sites live inside a {@code @Transactional} method that re-throws
 * an unchecked {@code ApplicationException} on the same path — Spring's
 * default rollback-on-unchecked-exception rule would otherwise undo exactly
 * the write that path exists to make, silently disabling account lockout and
 * stolen-refresh-token-family revocation. {@code REQUIRES_NEW} runs each as
 * its own transaction that commits independently before the caller's
 * transaction rolls back. This only works because {@code AuthenticationServiceImpl}
 * calls it as an injected collaborator, not a same-class method: Spring's
 * proxy-based {@code @Transactional} does not intercept self-invocation
 * ({@code this.someMethod()}), so this had to be a separate bean.
 */
@Service
class AuthSecurityEventRecorder {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    AuthSecurityEventRecorder(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** Returns true if this attempt crossed the threshold and locked the account, so the caller can audit-log it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean recordFailedAttempt(Long userId, int maxFailedAttempts, Duration lockoutDuration) {
        return userRepository.findById(userId).map(user -> {
            user.recordFailedLoginAttempt();
            boolean justLocked = user.getFailedAttempts() >= maxFailedAttempts;
            if (justLocked) {
                user.lockUntil(Instant.now().plus(lockoutDuration));
                user.setStatus(UserStatus.LOCKED);
            }
            userRepository.save(user);
            return justLocked;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void revokeFamily(String familyId) {
        Instant now = Instant.now();
        List<RefreshToken> family = refreshTokenRepository.findByFamilyId(familyId);
        family.stream().filter(token -> !token.isRevoked()).forEach(token -> token.revoke(now));
        refreshTokenRepository.saveAll(family);
    }
}
