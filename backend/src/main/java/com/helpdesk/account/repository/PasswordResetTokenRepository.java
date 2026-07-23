package com.helpdesk.account.repository;

import com.helpdesk.account.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code findByTokenHash} resolves the raw token a caller presents at
 * {@code /account/reset-password} (hashed first, then looked up) back to its
 * server-side record — the only way a reset token is ever looked up. It
 * intentionally returns whatever row matches the hash, expired/used or not;
 * deciding "missing," "expired," and "already used" all collapse to the same
 * caller-facing {@code InvalidTokenException} is a Service-layer judgment
 * (Milestone 4 design, Change 2), not something a derived query name should
 * encode. {@code deleteByExpiresAtBefore} backs the scheduled cleanup job
 * that purges long-expired rows so this table doesn't grow unbounded — same
 * shape as {@code RefreshTokenRepository.deleteByExpiresAtBefore}, a plain
 * derived delete needs no {@code @Modifying}/{@code @Query} of its own.
 * <p>
 * No "revoke all tokens for a user" method here: that concern belongs to
 * refresh tokens (via {@code AuthenticationService}, per the approved
 * design's Change 5), not to this repository.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(Instant cutoff);
}
