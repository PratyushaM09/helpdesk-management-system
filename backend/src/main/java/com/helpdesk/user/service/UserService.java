package com.helpdesk.user.service;

import com.helpdesk.user.dto.request.CreateUserRequest;
import com.helpdesk.user.dto.request.UpdateUserRequest;
import com.helpdesk.user.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The User module's public contract, expressed only in DTOs — never an
 * entity or repository type (11-Development-Rules.md §8.5) — so Controllers
 * and any future cross-module caller depend on this interface alone, not on
 * {@code UserServiceImpl}.
 */
public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    UserResponse getUserById(Long id);

    Page<UserResponse> getUsers(Pageable pageable);

    UserResponse updateUser(Long id, UpdateUserRequest request);
}
