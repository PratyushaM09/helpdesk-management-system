package com.helpdesk.role.service.impl;

import com.helpdesk.exception.ConflictException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.role.dto.request.UpdateRoleRequest;
import com.helpdesk.role.dto.response.RoleResponse;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.mapper.RoleMapper;
import com.helpdesk.role.repository.RoleRepository;
import com.helpdesk.role.service.RoleService;
import com.helpdesk.user.repository.UserRepository;
import com.helpdesk.util.SortValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class RoleServiceImpl implements RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleServiceImpl.class);

    /** Public, sortable columns only. */
    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("id", "name", "createdAt", "updatedAt");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RoleMapper roleMapper;

    public RoleServiceImpl(RoleRepository roleRepository, UserRepository userRepository, RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.roleMapper = roleMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        return roleMapper.toResponse(findRoleOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RoleResponse> getRoles(Pageable pageable) {
        SortValidator.validate(pageable.getSort(), ALLOWED_SORT_PROPERTIES);
        return roleRepository.findAll(pageable).map(roleMapper::toResponse);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long id, UpdateRoleRequest request) {
        Role role = findRoleOrThrow(id);
        role.setDescription(request.description());
        Role saved = roleRepository.save(role);

        log.info("Role updated: roleId={}", id);
        return roleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        Role role = findRoleOrThrow(id);

        if (role.isSystem()) {
            throw new ConflictException("System role cannot be deleted: '%s'".formatted(role.getName()));
        }
        if (userRepository.existsByRoleId(id)) {
            throw new ConflictException("Role is currently assigned to one or more users and cannot be deleted");
        }

        roleRepository.delete(role);
        log.info("Role deleted: roleId={}", id);
    }

    private Role findRoleOrThrow(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
    }
}
