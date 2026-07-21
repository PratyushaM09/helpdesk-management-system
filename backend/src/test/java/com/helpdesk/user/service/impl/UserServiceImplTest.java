package com.helpdesk.user.service.impl;

import com.helpdesk.exception.BadRequestException;
import com.helpdesk.exception.ConflictException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.user.dto.request.CreateUserRequest;
import com.helpdesk.user.dto.request.UpdateUserRequest;
import com.helpdesk.user.dto.response.UserResponse;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.mapper.UserMapper;
import com.helpdesk.role.repository.RoleRepository;
import com.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — every collaborator is mocked (11-Development-Rules.md
 * §16.1); no Spring context, no database. Mocking {@link UserMapper} itself
 * (rather than exercising its real, separately-tested implementation) means
 * each test controls the exact {@link UserResponse} a "mapped" entity
 * produces, without needing to poke a JPA-generated {@code id} field.
 */
class UserServiceImplTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserServiceImpl(userRepository, roleRepository, userMapper, passwordEncoder);
    }

    @Test
    void createUser_shouldHashPasswordAndPersist_whenEmailIsNotTaken() {
        CreateUserRequest request = new CreateUserRequest("Ada Lovelace", "ada@example.com", "Str0ng!Passw0rd", RoleName.USER);
        Role role = aRole(RoleName.USER);
        User transientUser = aUser(role);
        User savedUser = aUser(role);
        UserResponse expectedResponse = aResponse(1L, role.getName());

        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(roleRepository.findByName(RoleName.USER)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userMapper.toEntity(request, "hashed-password", role)).thenReturn(transientUser);
        when(userRepository.save(transientUser)).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(expectedResponse);

        UserResponse result = userService.createUser(request);

        assertEquals(expectedResponse, result);
        verify(passwordEncoder).encode(request.password());
        verify(userMapper).toEntity(request, "hashed-password", role);
        verify(userRepository).save(transientUser);
    }

    @Test
    void createUser_shouldThrowConflict_whenEmailAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest("Ada Lovelace", "ada@example.com", "Str0ng!Passw0rd", RoleName.USER);
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.createUser(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(roleRepository, passwordEncoder, userMapper);
    }

    @Test
    void getUserById_shouldReturnMappedUser_whenFound() {
        Role role = aRole(RoleName.USER);
        User user = aUser(role);
        UserResponse expectedResponse = aResponse(1L, role.getName());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        UserResponse result = userService.getUserById(1L);

        assertEquals(expectedResponse, result);
    }

    @Test
    void getUserById_shouldThrowNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(99L));

        verifyNoInteractions(userMapper);
    }

    @Test
    void getUsers_shouldReturnMappedPage_whenSortFieldIsAllowed() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
        Role role = aRole(RoleName.USER);
        User user = aUser(role);
        UserResponse response = aResponse(1L, role.getName());
        Page<User> userPage = new PageImpl<>(List.of(user), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(userPage);
        when(userMapper.toResponse(user)).thenReturn(response);

        Page<UserResponse> result = userService.getUsers(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(response, result.getContent().get(0));
    }

    @Test
    void getUsers_shouldThrowBadRequest_whenSortFieldIsNotAllowed() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("passwordHash"));

        assertThrows(BadRequestException.class, () -> userService.getUsers(pageable));

        verifyNoInteractions(userRepository, userMapper);
    }

    @Test
    void updateUser_shouldApplyChanges_whenEmailIsNotTakenByAnotherUser() {
        UpdateUserRequest request = new UpdateUserRequest("Ada L.", "ada.new@example.com", RoleName.SUPPORT_ENGINEER);
        Role existingRole = aRole(RoleName.USER);
        Role newRole = aRole(RoleName.SUPPORT_ENGINEER);
        User existingUser = aUser(existingRole);
        UserResponse expectedResponse = aResponse(1L, RoleName.SUPPORT_ENGINEER);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), 1L)).thenReturn(false);
        when(roleRepository.findByName(RoleName.SUPPORT_ENGINEER)).thenReturn(Optional.of(newRole));
        when(userRepository.save(existingUser)).thenReturn(existingUser);
        when(userMapper.toResponse(existingUser)).thenReturn(expectedResponse);

        UserResponse result = userService.updateUser(1L, request);

        assertEquals(expectedResponse, result);
        verify(userMapper).updateEntity(existingUser, request, newRole);
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUser_shouldThrowConflict_whenEmailBelongsToAnotherUser() {
        UpdateUserRequest request = new UpdateUserRequest("Ada L.", "taken@example.com", RoleName.USER);
        User existingUser = aUser(aRole(RoleName.USER));
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), 1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.updateUser(1L, request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userMapper, roleRepository);
    }

    @Test
    void updateUser_shouldThrowNotFound_whenUserDoesNotExist() {
        UpdateUserRequest request = new UpdateUserRequest("Name", "e@example.com", RoleName.USER);
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.updateUser(404L, request));

        verify(userRepository, never()).existsByEmailIgnoreCaseAndIdNot(any(), anyLong());
        verifyNoInteractions(userMapper, roleRepository);
    }

    @Test
    void deactivateUser_shouldSetStatusToDeactivated_whenUserExists() {
        User user = aUser(aRole(RoleName.USER));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deactivateUser(1L);

        assertEquals(UserStatus.DEACTIVATED, user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void deactivateUser_shouldThrowNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.deactivateUser(404L));

        verify(userRepository, never()).save(any());
    }

    private Role aRole(RoleName name) {
        return new Role(name, name.name());
    }

    private User aUser(Role role) {
        return new User("Ada Lovelace", "ada@example.com", "hashed-password", role);
    }

    private UserResponse aResponse(Long id, RoleName role) {
        Instant now = Instant.now();
        return new UserResponse(id, "Ada Lovelace", "ada@example.com", role, UserStatus.UNVERIFIED, now, now);
    }
}
