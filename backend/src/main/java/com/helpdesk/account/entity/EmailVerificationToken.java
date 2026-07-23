package com.helpdesk.account.entity;

import com.helpdesk.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One issued "verify your email" token (Milestone 4 design). Redeeming it
 * (Service layer, a later step) sets the owning {@link User}'s
 * {@code emailVerified}/{@code emailVerifiedAt} fields — deliberately never
 * {@code User.status}, which tracks account lifecycle (active/locked/
 * deactivated) as a separate concern from email verification (Milestone 4
 * design, Change 3). This entity itself has no opinion on either; it only
 * records the token's own lifecycle.
 * <p>
 * Same shape and same indexing rationale as {@link PasswordResetToken}:
 * inherited {@code tokenHash} uniqueness from {@code AbstractUserToken},
 * plus an explicit {@code expires_at} index for the future cleanup
 * scheduler's {@code expires_at < cutoff} deletion query. No explicit
 * {@code user_id} index — the {@code @JoinColumn} foreign key already
 * provides one.
 */
@Entity
@Table(name = "email_verification_tokens",
        indexes = @Index(name = "idx_email_verification_tokens_expires_at", columnList = "expires_at"))
public class EmailVerificationToken extends AbstractUserToken {

    protected EmailVerificationToken() {
    }

    public EmailVerificationToken(User user, String tokenHash, Instant expiresAt) {
        super(user, tokenHash, expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmailVerificationToken other)) {
            return false;
        }
        return getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "EmailVerificationToken{id=%d, expiresAt=%s, used=%s}"
                .formatted(getId(), getExpiresAt(), isUsed());
    }
}
