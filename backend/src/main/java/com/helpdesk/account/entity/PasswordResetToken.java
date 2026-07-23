package com.helpdesk.account.entity;

import com.helpdesk.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One issued "forgot password" reset token (Milestone 4 design, Change 6/7).
 * Redeeming it (Service layer, a later step) must encode/increment the
 * owning {@link User}'s {@code tokenVersion} and revoke that user's
 * refresh tokens in the same transaction — this entity only records the
 * token's own lifecycle, not that side effect.
 * <p>
 * Indexed on {@code expires_at} in addition to inherited
 * {@code AbstractUserToken.tokenHash}'s unique constraint: the future
 * cleanup scheduler (Milestone 4 design) deletes by
 * {@code expires_at < cutoff}, and unlike {@code RefreshToken} — which
 * accepted an unindexed cleanup scan for now — this table is explicitly
 * called out in the milestone's constraint requirements, so the index is
 * added deliberately rather than deferred. No explicit index on
 * {@code user_id}: the {@code @JoinColumn}'s foreign key constraint already
 * gets one automatically on InnoDB, the same assumption {@code RefreshToken}
 * relies on.
 */
@Entity
@Table(name = "password_reset_tokens",
        indexes = @Index(name = "idx_password_reset_tokens_expires_at", columnList = "expires_at"))
public class PasswordResetToken extends AbstractUserToken {

    protected PasswordResetToken() {
    }

    public PasswordResetToken(User user, String tokenHash, Instant expiresAt) {
        super(user, tokenHash, expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PasswordResetToken other)) {
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
        return "PasswordResetToken{id=%d, expiresAt=%s, used=%s}"
                .formatted(getId(), getExpiresAt(), isUsed());
    }
}
