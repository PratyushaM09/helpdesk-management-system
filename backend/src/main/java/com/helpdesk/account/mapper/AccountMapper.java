package com.helpdesk.account.mapper;

import com.helpdesk.account.dto.response.AccountProfileResponse;
import com.helpdesk.user.entity.User;

/**
 * Structural {@code User} → {@code AccountProfileResponse} conversion only
 * (11-Development-Rules.md §5.5) — no business rule, no repository access,
 * no password handling.
 * <p>
 * One direction only, unlike {@code UserMapper}: every write this module
 * performs (profile edit, password change, password reset, email
 * verification, activation) mutates an already-managed {@link User} through
 * its own named domain method ({@code setName}, {@code markEmailVerified},
 * {@code incrementTokenVersion}, ...), each entangled with a rule that isn't
 * structural — encoding a password, deciding whether a token is redeemable,
 * revoking refresh tokens in the same transaction. A mapper method can't
 * express "and also revoke every refresh token for this user," so there is
 * no {@code toEntity}/{@code updateEntity} here; the Service layer calls
 * those domain methods directly instead of going through this mapper.
 * <p>
 * Declared as an interface with a hand-written {@code AccountMapperImpl}
 * (same pre-MapStruct convention as {@code UserMapper}), so every call site
 * (`accountMapper.toAccountProfile(user)`) is unaffected if MapStruct is
 * adopted later.
 */
public interface AccountMapper {

    /**
     * Projects a {@link User} to its API-safe account-profile shape.
     * Deliberately has no line mapping {@code passwordHash}, refresh tokens,
     * {@code tokenVersion}, {@code failedAttempts}, or {@code lockedUntil} —
     * omission here is what keeps it structurally impossible for any of
     * those fields to reach a client, the same convention
     * {@code UserMapper.toResponse} follows.
     */
    AccountProfileResponse toAccountProfile(User user);
}
