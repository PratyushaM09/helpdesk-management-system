package com.helpdesk.auth.service.impl;

import com.helpdesk.auth.entity.RefreshToken;
import com.helpdesk.auth.repository.RefreshTokenRepository;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Where the failed-attempt-counting / lockout and refresh-token-family
 * revocation logic actually lives now (moved out of
 * {@link AuthenticationServiceImpl} so it survives that class's rollback -
 * see this class's own Javadoc). Pure unit test, same "mock the
 * repositories, use real domain objects" convention as
 * {@code AuthenticationServiceImplTest}.
 */
class AuthSecurityEventRecorderTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AuthSecurityEventRecorder recorder;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        recorder = new AuthSecurityEventRecorder(userRepository, refreshTokenRepository);
    }

    @Test
    void recordFailedAttempt_shouldIncrementAndReturnFalse_whenBelowThreshold() {
        User user = aUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        boolean locked = recorder.recordFailedAttempt(user.getId(), MAX_ATTEMPTS, LOCKOUT_DURATION);

        assertFalse(locked);
        assertEquals(1, user.getFailedAttempts());
        assertFalse(user.isLocked(Instant.now()));
        verify(userRepository).save(user);
    }

    @Test
    void recordFailedAttempt_shouldLockAndReturnTrue_whenThresholdReached() {
        User user = aUser();
        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            user.recordFailedLoginAttempt();
        }
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        boolean locked = recorder.recordFailedAttempt(user.getId(), MAX_ATTEMPTS, LOCKOUT_DURATION);

        assertTrue(locked);
        assertEquals(MAX_ATTEMPTS, user.getFailedAttempts());
        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertTrue(user.isLocked(Instant.now()));
        assertTrue(user.getLockedUntil().isAfter(Instant.now().plus(Duration.ofMinutes(14))));
        verify(userRepository).save(user);
    }

    @Test
    void recordFailedAttempt_shouldReturnFalse_andNeverSave_whenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        boolean locked = recorder.recordFailedAttempt(999L, MAX_ATTEMPTS, LOCKOUT_DURATION);

        assertFalse(locked);
        verify(userRepository, never()).save(any());
    }

    @Test
    void revokeFamily_shouldRevokeEveryUnrevokedMember() {
        User user = aUser();
        RefreshToken replayed = new RefreshToken(user, "hash-a", Instant.now().plus(Duration.ofDays(1)), "family-1");
        RefreshToken successor = new RefreshToken(user, "hash-b", Instant.now().plus(Duration.ofDays(1)), "family-1");
        RefreshToken alreadyRevokedSibling = new RefreshToken(user, "hash-c", Instant.now().plus(Duration.ofDays(1)), "family-1");
        alreadyRevokedSibling.revoke(Instant.now().minus(Duration.ofMinutes(5)));
        when(refreshTokenRepository.findByFamilyId("family-1"))
                .thenReturn(List.of(replayed, successor, alreadyRevokedSibling));

        recorder.revokeFamily("family-1");

        assertTrue(replayed.isRevoked());
        assertTrue(successor.isRevoked());
        assertTrue(alreadyRevokedSibling.isRevoked());
        verify(refreshTokenRepository).saveAll(anyList());
    }

    private User aUser() {
        Role role = new Role(RoleName.USER, "USER");
        User user = new User("Ada Lovelace", "ada@example.com", "hashed-password", role);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
