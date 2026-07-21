package com.helpdesk.role.service;

import com.helpdesk.role.dto.request.UpdateRoleRequest;
import com.helpdesk.role.dto.response.RoleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The Role module's public contract, expressed only in DTOs — never an
 * entity or repository type — so Controllers and any future cross-module
 * caller depend on this interface alone, not on {@code RoleServiceImpl}.
 * No {@code createRole}: {@code name} is a closed, seeded enum in this
 * milestone (Phase 2, Milestone 2 — fixed-roles-only scope), so there is
 * nothing for a create operation to accept beyond what {@code RoleSeeder}
 * already guarantees exists.
 */
public interface RoleService {

    RoleResponse getRoleById(Long id);

    Page<RoleResponse> getRoles(Pageable pageable);

    RoleResponse updateRole(Long id, UpdateRoleRequest request);

    /**
     * Rejected with a {@code ConflictException} if the role is a system role,
     * or if any user currently references it — a role is only ever actually
     * removed when both conditions are false.
     */
    void deleteRole(Long id);
}
