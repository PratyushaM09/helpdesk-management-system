package com.helpdesk.user.service.impl;

import com.helpdesk.exception.ConflictException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.user.dto.request.CreateUserRequest;
import com.helpdesk.user.dto.request.UpdateUserRequest;
import com.helpdesk.user.dto.response.UserResponse;
import com.helpdesk.user.entity.Role;
import com.helpdesk.user.entity.RoleName;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.mapper.UserMapper;
import com.helpdesk.user.repository.RoleRepository;
import com.helpdesk.user.repository.UserRepository;
import com.helpdesk.user.service.UserService;
import com.helpdesk.util.SortValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** Public, sortable columns only — never a sensitive/internal field such as passwordHash. */
    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("id", "name", "email", "status", "createdAt", "updatedAt");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                            UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email is already registered: '%s'".formatted(request.email()));
        }

        Role role = resolveRole(request.role());
        String passwordHash = passwordEncoder.encode(request.password());
        User user = userMapper.toEntity(request, passwordHash, role);
        User saved = userRepository.save(user);

        log.info("User created: userId={}, role={}", saved.getId(), role.getName());
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return userMapper.toResponse(findUserOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Pageable pageable) {
        SortValidator.validate(pageable.getSort(), ALLOWED_SORT_PROPERTIES);
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findUserOrThrow(id);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new ConflictException("Email is already registered: '%s'".formatted(request.email()));
        }

        Role role = resolveRole(request.role());
        userMapper.updateEntity(user, request, role);
        User saved = userRepository.save(user);

        log.info("User updated: userId={}", id);
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivateUser(Long id) {
        User user = findUserOrThrow(id);
        user.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(user);

        log.info("User deactivated: userId={}", id);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    /**
     * The three roles are seeded at startup (Milestone 1's minimal Role
     * slice), so an absent role here means the seed data itself is missing
     * — an internal invariant violation, not a client-caused case, hence an
     * unchecked exception rather than a named {@code ApplicationException}.
     */
    private Role resolveRole(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
    }
}
