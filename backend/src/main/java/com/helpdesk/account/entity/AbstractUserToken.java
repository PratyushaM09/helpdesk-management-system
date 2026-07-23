package com.helpdesk.account.entity;

import com.helpdesk.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Shared shape for a single-use, expiring, hashed, user-scoped secret —
 * {@link com.helpdesk.account.entity.PasswordResetToken} and
 * {@link com.helpdesk.account.entity.EmailVerificationToken} are the same
 * concept applied to two purposes, not two coincidentally similar entities.
 * That is a genuine is-a relationship with a stable, five-field contract
 * (11-Development-Rules.md §2.4), the same justification
 * {@code AuditableEntity} already relies on for {@code createdAt}/
 * {@code updatedAt} — this is a second, narrowly-scoped instance of that
 * same sanctioned pattern, not a departure from it.
 * <p>
 * Deliberately not shared with {@code RefreshToken}: that entity carries
 * {@code familyId}/{@code replacedBy} for rotation-chain tracking that
 * these two token types have no equivalent of, so forcing it into this
 * hierarchy would either strip those fields or leak auth-specific rotation
 * concepts into a shared account-module base class.
 * <p>
 * The raw token value handed to the user is never persisted — only
 * {@link #tokenHash}, the same "hash only, never the secret itself" rule
 * {@code RefreshToken.tokenHash} already follows.
 * <p>
 * {@code equals()}/{@code hashCode()} are intentionally <b>not</b> defined
 * here: each concrete subclass defines its own {@code instanceof <ExactType>}
 * check (matching {@code User}/{@code RefreshToken}'s existing pattern), so
 * that a {@code PasswordResetToken} and an {@code EmailVerificationToken}
 * that happen to share an id are never accidentally treated as equal.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractUserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** The account this token was issued for. Never eagerly loaded — every existing consumer already has the user in hand or only needs the id. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hex digest of the raw token (64 chars); the raw value itself is never stored, mirroring {@code RefreshToken.tokenHash}. Unique so a hash collision can never resolve to two live tokens. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** When this token stops being redeemable, regardless of whether it was ever used. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null until redeemed; non-null enforces single-use (SDR-style: a token is either unused-and-live, or permanently spent). */
    @Column(name = "used_at")
    private Instant usedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AbstractUserToken() {
    }

    protected AbstractUserToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** True once past {@link #expiresAt}, independent of whether it was ever used — an expired-but-unused token is still not redeemable. */
    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    /** True once redeemed. Belongs on the entity, not the Service, because "used" is purely a function of this row's own state ({@code usedAt != null}), not of anything external. */
    public boolean isUsed() {
        return usedAt != null;
    }

    /** Marks this token permanently spent. Callers (the future Service layer) decide *when* redemption happens; this method only ever records that it did. */
    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
