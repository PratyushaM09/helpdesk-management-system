package com.helpdesk.auth.entity;

import com.helpdesk.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Server-side record of one issued refresh token (ADR-0003, SDR-003) — the
 * opaque value handed to the client is never itself persisted, only its
 * SHA-256 hash ({@code tokenHash}), so a database read alone can never
 * reconstruct a usable token. Existence of this row (rather than a JWT-only
 * refresh design) is what makes logout and reuse-detection revocation
 * possible despite JWTs being otherwise unrevokable before natural expiry.
 * <p>
 * {@code replacedBy} forms the rotation chain (05-Database.md §2): every use
 * mints a new row and points this one at it via {@link #markReplacedBy}.
 * Presenting a token whose row already has {@code replacedBy} set is a
 * replay of an already-superseded token — the reuse-detection signal
 * SDR-003 exists to catch.
 * <p>
 * {@code familyId} is a UUID minted once at login and copied forward,
 * unchanged, on every rotation — it's what makes SDR-003's "revoke the
 * entire token family on reuse" actually implementable: {@code replacedBy}
 * alone only lets you walk <em>forward</em> from a chain's start, but reuse
 * is detected on an arbitrary, possibly-mid-chain token with no reachable
 * link back to its siblings. {@code familyId} is that reachable link — one
 * indexed query (see {@code RefreshTokenRepository.findByFamilyId}) finds
 * every token issued from the same original login, regardless of where in
 * the chain the replay was caught.
 * <p>
 * Deliberately does <b>not</b> extend {@code AuditableEntity}: this is a
 * security artifact with an append/revoke lifecycle, not a general mutable
 * business entity, and it has no legitimate "last modified for an arbitrary
 * reason" concept the way {@code User}/{@code Role} do — {@link #revoke}
 * and {@link #markReplacedBy} are the only two state changes that ever
 * occur, both already explicitly timestamped/chained in their own right.
 * An inherited {@code updatedAt} would only restate that same information
 * more vaguely, so only {@code createdAt} is carried, via the same JPA
 * auditing mechanism {@code AuditableEntity} itself uses.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private RefreshToken replacedBy;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt, String familyId) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.familyId = familyId;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public RefreshToken getReplacedBy() {
        return replacedBy;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** True once this token has been rotated — presenting it again is a reuse-detection event (SDR-003). */
    public boolean isReplaced() {
        return replacedBy != null;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    public void markReplacedBy(RefreshToken newToken) {
        this.replacedBy = newToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RefreshToken other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "RefreshToken{id=%d, expiresAt=%s, revoked=%s, replaced=%s}"
                .formatted(id, expiresAt, isRevoked(), isReplaced());
    }
}
