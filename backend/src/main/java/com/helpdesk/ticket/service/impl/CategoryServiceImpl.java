package com.helpdesk.ticket.service.impl;

import com.helpdesk.exception.BadRequestException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.ticket.dto.response.CategoryResponse;
import com.helpdesk.ticket.entity.Category;
import com.helpdesk.ticket.mapper.CategoryMapper;
import com.helpdesk.ticket.repository.CategoryRepository;
import com.helpdesk.ticket.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    /**
     * In-memory filter over {@code findAll()} rather than a new
     * {@code CategoryRepository.findByActiveTrue} query method - the
     * Repository Layer milestone deliberately left this repository at
     * {@code findByNameIgnoreCase} only, and the seeded category list is a
     * fixed ~8 rows (FR-PRI-2), so a repository-level query would add a
     * method for a scale where it makes no measurable difference. Revisit if
     * categories ever become admin-creatable in volume.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findAll().stream()
                .filter(Category::isActive)
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        return categoryMapper.toResponse(findCategoryOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public void validateActiveCategory(Long id) {
        Category category = findCategoryOrThrow(id);
        if (!category.isActive()) {
            throw new BadRequestException("Category is not active: '%s'".formatted(category.getName()));
        }
    }

    private Category findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }
}
