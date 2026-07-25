package com.helpdesk.ticket.service.impl;

import com.helpdesk.exception.BadRequestException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.ticket.dto.response.CategoryResponse;
import com.helpdesk.ticket.entity.Category;
import com.helpdesk.ticket.mapper.CategoryMapper;
import com.helpdesk.ticket.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — every collaborator is mocked, matching {@code RoleServiceImplTest}'s
 * convention; no Spring context, no database. No {@code SecurityContextHolder}
 * setup needed — unlike {@code CommentServiceImpl}/{@code AttachmentServiceImpl},
 * {@code CategoryServiceImpl} never reads the authenticated caller.
 */
class CategoryServiceImplTest {

    private CategoryRepository categoryRepository;
    private CategoryMapper categoryMapper;
    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        categoryMapper = mock(CategoryMapper.class);
        categoryService = new CategoryServiceImpl(categoryRepository, categoryMapper);
    }

    // --- getActiveCategories ---

    @Test
    void getActiveCategories_shouldReturnOnlyActiveCategories_whenSomeAreInactive() {
        Category active1 = aCategory("Software");
        Category active2 = aCategory("Hardware");
        Category inactive = aCategory("Retired");
        inactive.deactivate();
        CategoryResponse response1 = aCategoryResponse(1L, "Software");
        CategoryResponse response2 = aCategoryResponse(2L, "Hardware");
        when(categoryRepository.findAll()).thenReturn(List.of(active1, active2, inactive));
        when(categoryMapper.toResponse(active1)).thenReturn(response1);
        when(categoryMapper.toResponse(active2)).thenReturn(response2);

        List<CategoryResponse> result = categoryService.getActiveCategories();

        assertEquals(List.of(response1, response2), result);
        verify(categoryMapper, never()).toResponse(inactive);
    }

    @Test
    void getActiveCategories_shouldReturnEmptyList_whenNoCategoriesExist() {
        when(categoryRepository.findAll()).thenReturn(List.of());

        List<CategoryResponse> result = categoryService.getActiveCategories();

        assertEquals(List.of(), result);
        verifyNoInteractions(categoryMapper);
    }

    @Test
    void getActiveCategories_shouldReturnEmptyList_whenEveryCategoryIsInactive() {
        Category inactive1 = aCategory("Retired1");
        inactive1.deactivate();
        Category inactive2 = aCategory("Retired2");
        inactive2.deactivate();
        when(categoryRepository.findAll()).thenReturn(List.of(inactive1, inactive2));

        List<CategoryResponse> result = categoryService.getActiveCategories();

        assertEquals(List.of(), result);
        verifyNoInteractions(categoryMapper);
    }

    // --- getCategoryById ---

    @Test
    void getCategoryById_shouldReturnMappedCategory_whenFound() {
        Category category = aCategory("Software");
        CategoryResponse expected = aCategoryResponse(1L, "Software");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryMapper.toResponse(category)).thenReturn(expected);

        CategoryResponse result = categoryService.getCategoryById(1L);

        assertEquals(expected, result);
    }

    @Test
    void getCategoryById_shouldThrowNotFound_whenCategoryDoesNotExist() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategoryById(404L));

        verifyNoInteractions(categoryMapper);
    }

    // --- validateActiveCategory ---

    @Test
    void validateActiveCategory_shouldNotThrow_whenCategoryIsActive() {
        Category category = aCategory("Software");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertDoesNotThrow(() -> categoryService.validateActiveCategory(1L));
    }

    @Test
    void validateActiveCategory_shouldThrowBadRequest_whenCategoryIsInactive() {
        Category category = aCategory("Retired");
        category.deactivate();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertThrows(BadRequestException.class, () -> categoryService.validateActiveCategory(1L));
    }

    @Test
    void validateActiveCategory_shouldThrowNotFound_whenCategoryDoesNotExist() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.validateActiveCategory(404L));
    }

    // --- fixtures ---

    private Category aCategory(String name) {
        Category category = new Category(name, name + " description");
        assertTrue(category.isActive());
        return category;
    }

    private CategoryResponse aCategoryResponse(Long id, String name) {
        return new CategoryResponse(id, name, name + " description");
    }
}
