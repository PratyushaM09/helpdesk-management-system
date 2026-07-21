package com.helpdesk.role.mapper;

import com.helpdesk.role.dto.response.RoleResponse;
import com.helpdesk.role.entity.Role;

/**
 * Converts {@link Role} entities into API response DTOs.
 * <p>
 * Contains mapping logic only. Business rules belong in the service layer.
 */
public interface RoleMapper {

    /** Projects a {@link Role} to its API-safe response shape. */
    RoleResponse toResponse(Role role);
}
