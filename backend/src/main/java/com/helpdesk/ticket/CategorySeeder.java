package com.helpdesk.ticket;

import com.helpdesk.ticket.entity.Category;
import com.helpdesk.ticket.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the fixed starter category list (FR-PRI-2) once on startup, if
 * missing — idempotent, same shape as {@code RoleSeeder}. No FK dependency on
 * {@code RoleSeeder}/{@code UserSeeder}, but ordered explicitly anyway
 * ({@code @Order(3)}), consistent with this codebase's "every seeder's
 * relative order is stated, not implicit" convention.
 */
@Component
@Order(3)
public class CategorySeeder {

    private static final Logger log = LoggerFactory.getLogger(CategorySeeder.class);

    private final CategoryRepository categoryRepository;

    public CategorySeeder(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedCategories() {
        seedIfMissing("Software", "Application/software issues.");
        seedIfMissing("Hardware", "Physical device/equipment issues.");
        seedIfMissing("Network", "Connectivity and network infrastructure issues.");
        seedIfMissing("Email", "Email service and mailbox issues.");
        seedIfMissing("Accounts", "Account access and credential issues.");
        seedIfMissing("Security", "Security incidents and concerns.");
        seedIfMissing("Infrastructure", "Core infrastructure and systems issues.");
        seedIfMissing("Other", "Anything not covered by the categories above.");
    }

    private void seedIfMissing(String name, String description) {
        if (categoryRepository.findByNameIgnoreCase(name).isEmpty()) {
            categoryRepository.save(new Category(name, description));
            log.info("Seeded category: {}", name);
        }
    }
}
