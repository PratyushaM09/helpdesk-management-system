package com.helpdesk.ticket.controller;

import com.helpdesk.common.ApiResponse;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.ticket.dto.response.CategoryResponse;
import com.helpdesk.ticket.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Category reference data (Phase 3, Controller Milestone 1). No create/
 * rename/deactivate endpoints - categories remain seeded reference data
 * (05-Database.md §8), administered only by {@code CategorySeeder} in this
 * phase. Not paginated - {@link CategoryService#getActiveCategories()}
 * already returns a plain {@code List} (a fixed ~8-row reference list, not a
 * growing collection).
 */
@Tag(name = "Category")
@RestController
@RequestMapping(ApiConstants.API_BASE_PATH + "/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "List active categories", description = "Used by the ticket-creation category picker. Deactivated categories are excluded.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Active category list")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getActiveCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getActiveCategories()));
    }
}
