package com.helpdesk.role.mapper;

import com.helpdesk.role.dto.response.RoleResponse;
import com.helpdesk.role.entity.Role;
import org.springframework.stereotype.Component;

@Component
public class RoleMapperImpl implements RoleMapper {

    @Override
    public RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isSystem(),
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}
