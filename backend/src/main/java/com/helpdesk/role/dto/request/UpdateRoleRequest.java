package com.helpdesk.role.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/roles/{id}} request body.
 * <p>
 * Deliberately the only mutable Role field — {@code name} is fixed at seed
 * time and {@code system} is never client-settable, so there is nothing
 * else for this request to carry.
 *
 * @param description entity column {@code description}, max 255
 */
public record UpdateRoleRequest(
        @NotBlank
        @Size(max = 255)
        String description
) {
}
