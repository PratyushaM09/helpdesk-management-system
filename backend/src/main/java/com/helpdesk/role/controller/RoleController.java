package com.helpdesk.role.controller;

import com.helpdesk.common.ApiResponse;
import com.helpdesk.common.PageResponse;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.constant.ResponseMessages;
import com.helpdesk.role.dto.request.UpdateRoleRequest;
import com.helpdesk.role.dto.response.RoleResponse;
import com.helpdesk.role.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role Domain administration (Phase 2, Milestone 2). No {@code POST}:
 * {@code name} is a closed, seeded enum in this milestone, so there is no
 * create operation.
 * <p>
 * Every endpoint requires {@code ADMIN}, enforced via {@code @PreAuthorize}
 * on each method (Phase 2, Milestone 5) — same convention
 * {@code UserController} follows.
 */
@Tag(name = "Role")
@RestController
@RequestMapping(ApiConstants.API_BASE_PATH + "/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "Get a role by id")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Role not found")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRole(
            @Parameter(description = "Role identifier", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getRoleById(id)));
    }

    @Operation(summary = "List roles, paginated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paged role list")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> getRoles(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<RoleResponse> roles = roleService.getRoles(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(roles)));
    }

    @Operation(summary = "Update a role's description")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Role not found")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @Parameter(description = "Role identifier", example = "1") @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        RoleResponse updated = roleService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.UPDATED, updated));
    }

    @Operation(summary = "Delete a role",
            description = "Rejected if the role is a system role, or if any user is currently assigned to it.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Role not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Role is a system role or is still assigned to users")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @Parameter(description = "Role identifier", example = "1") @PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.DELETED));
    }
}
