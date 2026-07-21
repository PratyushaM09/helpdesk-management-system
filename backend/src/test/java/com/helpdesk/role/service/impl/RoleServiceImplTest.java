package com.helpdesk.role.service.impl;

import com.helpdesk.exception.BadRequestException;
import com.helpdesk.exception.ConflictException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.role.dto.request.UpdateRoleRequest;
import com.helpdesk.role.dto.response.RoleResponse;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.role.mapper.RoleMapper;
import com.helpdesk.role.repository.RoleRepository;
import com.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — every collaborator is mocked, matching
 * {@code UserServiceImplTest}'s convention; no Spring context, no database.
 * Mocking {@link RoleMapper} itself (rather than exercising its real,
 * separately-tested implementation) means each test controls the exact
 * {@link RoleResponse} a "mapped" entity produces, without needing to poke a
 * JPA-generated {@code id} field.
 */
class RoleServiceImplTest {

    private RoleRepository roleRepository;
    private UserRepository userRepository;
    private RoleMapper roleMapper;
    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        userRepository = mock(UserRepository.class);
        roleMapper = mock(RoleMapper.class);
        roleService = new RoleServiceImpl(roleRepository, userRepository, roleMapper);
    }

    @Test
    void getRoleById_shouldReturnMappedRole_whenFound() {
        Role role = aRole(RoleName.ADMIN);
        RoleResponse expectedResponse = aRoleResponse(1L, RoleName.ADMIN);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(roleMapper.toResponse(role)).thenReturn(expectedResponse);

        RoleResponse result = roleService.getRoleById(1L);

        assertEquals(expectedResponse, result);
    }

    @Test
    void getRoleById_shouldThrowNotFound_whenRoleDoesNotExist() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> roleService.getRoleById(99L));

        verifyNoInteractions(roleMapper);
    }

    @Test
    void getRoles_shouldReturnMappedPage_whenSortFieldIsAllowed() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
        Role role1 = aRole(RoleName.USER);
        Role role2 = aRole(RoleName.ADMIN);
        RoleResponse response1 = aRoleResponse(1L, RoleName.USER);
        RoleResponse response2 = aRoleResponse(2L, RoleName.ADMIN);
        Page<Role> rolePage = new PageImpl<>(List.of(role1, role2), pageable, 2);
        when(roleRepository.findAll(pageable)).thenReturn(rolePage);
        when(roleMapper.toResponse(role1)).thenReturn(response1);
        when(roleMapper.toResponse(role2)).thenReturn(response2);

        Page<RoleResponse> result = roleService.getRoles(pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals(List.of(response1, response2), result.getContent());
        verify(roleRepository, times(1)).findAll(pageable);
        verify(roleMapper).toResponse(role1);
        verify(roleMapper).toResponse(role2);
    }

    @Test
    void getRoles_shouldThrowBadRequest_whenSortFieldIsNotAllowed() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("description"));

        assertThrows(BadRequestException.class, () -> roleService.getRoles(pageable));

        verifyNoInteractions(roleRepository, roleMapper);
    }

    @Test
    void updateRole_shouldChangeOnlyDescription_whenRoleExists() {
        UpdateRoleRequest request = new UpdateRoleRequest("Updated description");
        Role existingRole = aRole(RoleName.SUPPORT_ENGINEER);
        RoleResponse expectedResponse = aRoleResponse(1L, RoleName.SUPPORT_ENGINEER);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existingRole));
        when(roleRepository.save(existingRole)).thenReturn(existingRole);
        when(roleMapper.toResponse(existingRole)).thenReturn(expectedResponse);

        RoleResponse result = roleService.updateRole(1L, request);

        assertEquals(expectedResponse, result);
        assertEquals("Updated description", existingRole.getDescription());
        assertEquals(RoleName.SUPPORT_ENGINEER, existingRole.getName());
        assertTrue(existingRole.isSystem());
        verify(roleRepository).save(existingRole);
    }

    @Test
    void updateRole_shouldThrowNotFound_whenRoleDoesNotExist() {
        UpdateRoleRequest request = new UpdateRoleRequest("Updated description");
        when(roleRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> roleService.updateRole(404L, request));

        verify(roleRepository, never()).save(any());
        verifyNoInteractions(roleMapper);
    }

    @Test
    void deleteRole_shouldThrowConflict_whenRoleIsSystemRole() {
        Role systemRole = aRole(RoleName.ADMIN, true);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(systemRole));

        assertThrows(ConflictException.class, () -> roleService.deleteRole(1L));

        verify(roleRepository, never()).delete(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void deleteRole_shouldThrowConflict_whenNonSystemRoleIsAssignedToUsers() {
        Role nonSystemRole = aRole(RoleName.SUPPORT_ENGINEER, false);
        when(roleRepository.findById(2L)).thenReturn(Optional.of(nonSystemRole));
        when(userRepository.existsByRoleId(2L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> roleService.deleteRole(2L));

        verify(roleRepository, never()).delete(any());
    }

    @Test
    void deleteRole_shouldDelete_whenNonSystemRoleIsNotAssignedToAnyUser() {
        Role nonSystemRole = aRole(RoleName.SUPPORT_ENGINEER, false);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(nonSystemRole));
        when(userRepository.existsByRoleId(3L)).thenReturn(false);

        roleService.deleteRole(3L);

        verify(roleRepository).delete(nonSystemRole);
    }

    @Test
    void deleteRole_shouldThrowNotFound_whenRoleDoesNotExist() {
        when(roleRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> roleService.deleteRole(404L));

        verify(roleRepository, never()).delete(any());
        verifyNoInteractions(userRepository);
    }

    private Role aRole(RoleName name) {
        return new Role(name, name.name());
    }

    private Role aRole(RoleName name, boolean system) {
        return new Role(name, name.name(), system);
    }

    private RoleResponse aRoleResponse(Long id, RoleName name) {
        Instant now = Instant.now();
        return new RoleResponse(id, name, name.name(), true, now, now);
    }
}
