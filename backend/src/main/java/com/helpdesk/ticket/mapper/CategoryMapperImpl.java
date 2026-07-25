package com.helpdesk.ticket.mapper;

import com.helpdesk.ticket.dto.response.CategoryResponse;
import com.helpdesk.ticket.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapperImpl implements CategoryMapper {

    @Override
    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }
}
