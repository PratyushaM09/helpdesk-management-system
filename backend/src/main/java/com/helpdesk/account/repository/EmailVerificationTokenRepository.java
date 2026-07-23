package com.helpdesk.account.repository;

import com.helpdesk.account.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Mirrors {@link com.helpdesk.account.repository.PasswordResetTokenRepository}'s
 * shape and reasoning exactly, applied to email verification tokens instead
 * of password reset tokens. {@code findByTokenHash} resolves the raw token
 * presented at {@code /account/verify-email} (hashed first) back to its
 * record, regardless of its expired/used state — that judgment is the
 * Service layer's, using the same shared {@code InvalidTokenException} for
 * missing/expired/used (Milestone 4 design, Change 2).
 * {@code deleteByExpiresAtBefore} backs this token type's own scheduled
 * cleanup job.
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(Instant cutoff);
}
