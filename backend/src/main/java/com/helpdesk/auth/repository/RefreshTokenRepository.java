package com.helpdesk.auth.repository;

import com.helpdesk.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@code findByTokenHash} resolves the opaque value a client presents at
 * {@code /auth/refresh} back to its server-side record (SDR-003) — the only
 * way a refresh token is ever looked up. {@code findByFamilyId} backs
 * reuse-detection's "revoke the entire family" response (see
 * {@link RefreshToken}'s {@code familyId} Javadoc) — the Service layer loads
 * the family, filters to not-yet-revoked members, and revokes each; kept as
 * a plain derived read rather than a bulk {@code @Modifying} update so the
 * revocation decision stays in the Service layer, not the query.
 * {@code findByUserId} backs {@code AuthenticationService.revokeAllRefreshTokensForUser}
 * (Milestone 4, Change 5) the same way: the Service loads every token this
 * user has ever been issued, filters to not-yet-revoked, and revokes each —
 * a plain derived read, not a bulk update, for the same reason. {@code
 * deleteByExpiresAtBefore} backs the scheduled cleanup job that purges
 * long-expired rows so this table doesn't grow unbounded.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyId(String familyId);

    List<RefreshToken> findByUserId(Long userId);

    void deleteByExpiresAtBefore(Instant cutoff);
}
