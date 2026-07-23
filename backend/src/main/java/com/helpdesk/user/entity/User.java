package com.helpdesk.user.entity;

import com.helpdesk.common.entity.AuditableEntity;
import com.helpdesk.role.entity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Every registered principal (User, Support Engineer, Administrator —
 * 05-Database.md §1). Carries the authentication-mechanism fields
 * (failed-attempt counter, lockout window, token version) the Authentication
 * milestone (Phase 2, Milestone 3) needs — {@code status} already reserved
 * {@link UserStatus#LOCKED} for exactly this before those fields existed.
 */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // Roles are fixed, seeded reference rows that are never deleted, so no
    // explicit ON DELETE clause is declared here - the database's implicit
    // default (reject the delete while a referencing user exists) already
    // matches the RESTRICT-shaped pattern used throughout 05-Database.md §5.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /** Consecutive bad-password count since the last successful login (SDR-005); reset on success. */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    /** Non-null while locked out; SDR-005's 15-minute sliding window. Checked by {@link #isLocked(Instant)}. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /**
     * Bumped to invalidate every currently-outstanding access token for this
     * user on its next verification (07-Security-Architecture.md §5.4) —
     * refresh tokens are separately, fully revoked by the same triggering
     * event, not just implicitly stale. Every caller that bumps this must
     * also revoke this user's live {@code RefreshToken} rows in the same
     * transaction; incrementing this field alone is not itself a "log out
     * everywhere," only the access-token half of it.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    /**
     * Whether this user has confirmed ownership of {@link #email} (Milestone 4
     * design, Change 3). Deliberately tracked independently of {@link #status}:
     * email verification and account lifecycle (active/locked/deactivated) are
     * separate concerns, so a deactivated account's verification history isn't
     * lost, and an unverified account isn't forced into a fabricated lifecycle
     * state just to represent "hasn't verified yet."
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /** Non-null once {@link #emailVerified} becomes true; set together, in {@link #markEmailVerified}. */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    protected User() {
    }

    public User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Replaces the stored hash with an already-encoded value — never a raw
     * password, the same "Service encodes, entity only stores" division the
     * constructor already follows. No plain {@code setPasswordHash}: every
     * password change is a security-relevant event worth a named method,
     * the same reasoning {@link #incrementTokenVersion()} and
     * {@link #markEmailVerified(Instant)} follow, unlike the plain
     * {@link #setName(String)}/{@link #setEmail(String)} CRUD setters.
     */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    /** True while {@code lockedUntil} is set and still in the future — the sliding-window check SDR-005 specifies. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordFailedLoginAttempt() {
        this.failedAttempts++;
    }

    /** Called on successful login (SDR-005) — the counter resets only on success, never merely on window expiry. */
    public void resetFailedAttempts() {
        this.failedAttempts = 0;
    }

    public void lockUntil(Instant until) {
        this.lockedUntil = until;
    }

    public void unlock() {
        this.lockedUntil = null;
        this.failedAttempts = 0;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    /**
     * Invalidates every currently-outstanding access token for this user on
     * its next verification (07-Security-Architecture.md §5.4). The Service
     * layer is responsible for deciding when this is warranted and for
     * revoking this user's live {@code RefreshToken} rows in the same
     * transaction — this method only ever does the one thing its name says.
     * Every event that should invalidate all issued access tokens calls it,
     * specifically:
     * <ul>
     *   <li><b>Password change</b> (deferred to a future milestone) — an
     *       unexpected password change is itself a signal worth forcing
     *       re-authentication everywhere (07-Security-Architecture.md §3.6).</li>
     *   <li><b>Password reset</b> (deferred to a future milestone) — a
     *       compromised-password scenario must not leave old sessions alive
     *       anywhere (07-Security-Architecture.md §3.5).</li>
     *   <li><b>Forced logout</b> — an explicit "log out this session
     *       everywhere" action, distinct from the single-session revocation
     *       {@code POST /auth/logout} performs.</li>
     *   <li><b>Administrator security action</b> — e.g., a role change or a
     *       suspected-compromise response, where continuing to honor an
     *       already-issued token under the old privilege level is unsafe.</li>
     *   <li>Any future event with the same shape: "this user's outstanding
     *       tokens are no longer trustworthy, force re-authentication."</li>
     * </ul>
     */
    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    /**
     * Records that this user has confirmed ownership of {@link #email}. The
     * Service layer is responsible for deciding when this is warranted (a
     * redeemed {@code EmailVerificationToken}, Milestone 4 design) — this
     * method only ever records that it happened, the same division of
     * responsibility {@link #incrementTokenVersion()} follows.
     * <p>
     * Guarded, not reapplied: a second call (e.g. a re-submitted or
     * concurrently-redeemed verification token) is not an error, but it must
     * not overwrite {@code emailVerifiedAt} with a later time — the field
     * means "when this first became true," not "when this was last
     * confirmed."
     */
    public void markEmailVerified(Instant now) {
        if (!emailVerified) {
            this.emailVerified = true;
            this.emailVerifiedAt = now;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
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
        return "User{id=%d, email=%s, status=%s}".formatted(id, email, status);
    }
}
