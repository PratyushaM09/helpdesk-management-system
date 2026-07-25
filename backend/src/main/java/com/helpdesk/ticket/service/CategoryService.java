package com.helpdesk.ticket.service;

import com.helpdesk.ticket.dto.response.CategoryResponse;

import java.util.List;

/**
 * Read-only access to seeded {@code Category} reference data (05-Database.md
 * §8) - no create/rename/deactivate operations in this phase; categories are
 * managed only by {@code CategorySeeder}. Never returns a {@code Category}
 * entity (02-Architecture.md §3: a Service interface's signature is
 * DTOs/void only) - {@code TicketServiceImpl} resolves the entity it needs
 * for ticket construction through its own, same-module
 * {@code CategoryRepository} access, not through this interface.
 */
public interface CategoryService {

    /** Categories usable for new-ticket creation - excludes deactivated ones. */
    List<CategoryResponse> getActiveCategories();

    /** Any category by id, active or not (a lookup/detail view, not a creation-eligibility check). */
    CategoryResponse getCategoryById(Long id);

    /**
     * @throws com.helpdesk.exception.ResourceNotFoundException if no category has this id
     * @throws com.helpdesk.exception.BadRequestException       if the category exists but is deactivated
     */
    void validateActiveCategory(Long id);
}
