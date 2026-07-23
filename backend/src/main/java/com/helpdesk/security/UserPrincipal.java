package com.helpdesk.security;

import com.helpdesk.role.entity.RoleName;
import com.helpdesk.user.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The thin, purpose-built {@code UserDetails} projection 08-Security-Controls.md
 * §8.4 specifies — deliberately not the {@code User} entity itself. Carries
 * exactly the six fields that section names: {@code userId}, {@code email},
 * {@code passwordHash}, {@code role}, {@code status}, {@code tokenVersion}.
 * No name, no avatar, no other profile data — the security layer has no use
 * for them, and this type is structurally incapable of leaking them through
 * a future security-layer bug because it simply has no field to leak.
 * <p>
 * {@code lockedUntil}/{@code failedAttempts} are deliberately absent too:
 * {@link #isAccountNonLocked()} is derived from {@code status} alone
 * (matching the cited section's field list exactly), not from the raw
 * counters — those stay on the entity, read only by the Service layer that
 * decides lockout policy.
 */
public record UserPrincipal(
        Long userId,
        String email,
        String passwordHash,
        RoleName role,
        UserStatus status,
        int tokenVersion
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    /** Always true — SDR-016: no forced periodic password expiration. */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Gated on {@code status} alone, not email verification — the two are
     * deliberately separate concerns (Milestone 4 design, Change 3), so an
     * unverified-but-{@code ACTIVE} account can still log in. Whether an
     * endpoint additionally requires a verified email is that endpoint's own
     * decision, not something this principal enforces globally.
     */
    @Override
    public boolean isEnabled() {
        return status != UserStatus.DEACTIVATED;
    }
}
