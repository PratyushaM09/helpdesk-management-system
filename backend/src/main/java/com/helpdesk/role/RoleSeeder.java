package com.helpdesk.role;

import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.role.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Seeds the three fixed roles (05-Database.md §1: reference/seed data) once
 * on startup, if they don't already exist — idempotent, so restarting the
 * application never creates duplicates. This is what makes
 * {@code UserServiceImpl.resolveRole}'s "the seed data is always present"
 * assumption actually true rather than a runtime gamble. The two-arg
 * {@code Role} constructor defaults {@code system = true} — the only source
 * of Role rows until a create-role endpoint exists.
 * <p>
 * Follows the same {@code @EventListener(ApplicationReadyEvent.class)}
 * shape already used by {@code ApplicationStartupListener} — one
 * established pattern for "run once at startup," not a second one.
 */
@Component
public class RoleSeeder {

    private static final Logger log = LoggerFactory.getLogger(RoleSeeder.class);

    private final RoleRepository roleRepository;

    public RoleSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedRoles() {
        seedIfMissing(RoleName.USER, "Standard requester - creates and tracks their own tickets.");
        seedIfMissing(RoleName.SUPPORT_ENGINEER, "Resolves tickets assigned to them.");
        seedIfMissing(RoleName.ADMIN, "Platform owner - manages users, roles, and categories.");
    }

    private void seedIfMissing(RoleName name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(new Role(name, description));
            log.info("Seeded role: {}", name);
        }
    }
}
