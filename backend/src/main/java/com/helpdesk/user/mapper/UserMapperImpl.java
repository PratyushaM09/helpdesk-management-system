package com.helpdesk.user.mapper;

import com.helpdesk.role.entity.Role;
import com.helpdesk.user.dto.request.CreateUserRequest;
import com.helpdesk.user.dto.request.UpdateUserRequest;
import com.helpdesk.user.dto.response.UserResponse;
import com.helpdesk.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public User toEntity(CreateUserRequest request, String passwordHash, Role role) {
        return new User(request.name(), request.email(), passwordHash, role);
    }

    @Override
    public void updateEntity(User user, UpdateUserRequest request, Role role) {
        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(role);
    }

    @Override
    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().getName(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
