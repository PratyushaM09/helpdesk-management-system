package com.helpdesk.user;

import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.role.repository.RoleRepository;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Seeds exactly one {@code ADMIN} account on startup, if none exists yet —
 * the answer to "who creates the first Administrator" once
 * {@code /api/v1/users} is locked to {@code ADMIN}-only (a future RBAC
 * step): without this, nothing could ever call that endpoint to create the
 * account needed to call it in the first place.
 * <p>
 * Idempotency check reuses {@code UserRepository.existsByRoleId}, the same
 * method {@code RoleServiceImpl}'s delete guard already uses (Phase 2
 * Milestone 2) — "does any user hold the ADMIN role" rather than "does a
 * user with this exact email exist," so re-running against a database where
 * an operator already created a differently-emailed Administrator by other
 * means still correctly skips seeding.
 * <p>
 * {@code @Order(2)}: must run after {@code RoleSeeder} ({@code @Order(1)}),
 * which is what makes the {@code ADMIN} role this class resolves actually
 * exist — {@code @EventListener} methods for the same event have no
 * guaranteed relative order otherwise.
 */
@Component
@Order(2)
public class UserSeeder {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    public UserSeeder(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, AdminBootstrapProperties properties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedAdmin() {
        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded - RoleSeeder must run first"));

        if (userRepository.existsByRoleId(adminRole.getId())) {
            return;
        }

        User admin = new User("Administrator", properties.email(), passwordEncoder.encode(properties.password()), adminRole);
        admin.setStatus(UserStatus.ACTIVE);
        // No email flow exists for the bootstrap account - seeded pre-verified
        // rather than left permanently unverifiable.
        admin.markEmailVerified(Instant.now());
        userRepository.save(admin);
        log.warn("Seeded bootstrap admin account: email={} - change its password immediately in any shared environment.",
                properties.email());
    }
}
