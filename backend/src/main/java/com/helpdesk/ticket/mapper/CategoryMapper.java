package com.helpdesk.ticket.mapper;

import com.helpdesk.ticket.dto.response.CategoryResponse;
import com.helpdesk.ticket.entity.Category;

/**
 * Converts {@link Category} entities into API response DTOs. No reverse
 * mapping — categories are seeded data ({@code CategorySeeder}), never
 * created from a request DTO in this phase.
 */
public interface CategoryMapper {

    /** Projects a {@link Category} to its API-safe response shape. */
    CategoryResponse toResponse(Category category);
}
