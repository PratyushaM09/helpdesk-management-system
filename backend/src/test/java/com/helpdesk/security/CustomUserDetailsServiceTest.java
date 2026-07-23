package com.helpdesk.security;

import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — {@link UserRepository} mocked, matching
 * {@code UserServiceImplTest}'s convention.
 */
class CustomUserDetailsServiceTest {

    private UserRepository userRepository;
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        customUserDetailsService = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_shouldReturnMappedPrincipal_whenFound() {
        User user = aUser(RoleName.ADMIN);
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        UserPrincipal principal = (UserPrincipal) customUserDetailsService.loadUserByUsername("ada@example.com");

        assertEquals(user.getEmail(), principal.email());
        assertEquals(user.getPasswordHash(), principal.passwordHash());
        assertEquals(RoleName.ADMIN, principal.role());
        assertEquals(user.getStatus(), principal.status());
        assertEquals(user.getTokenVersion(), principal.tokenVersion());
    }

    @Test
    void loadUserByUsername_shouldThrowUsernameNotFound_whenAbsent() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("ghost@example.com"));
    }

    @Test
    void loadUserById_shouldReturnMappedPrincipal_whenFound() {
        User user = aUser(RoleName.SUPPORT_ENGINEER);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        UserPrincipal principal = (UserPrincipal) customUserDetailsService.loadUserById(7L);

        assertEquals(user.getEmail(), principal.email());
        assertEquals(RoleName.SUPPORT_ENGINEER, principal.role());
    }

    @Test
    void loadUserById_shouldThrowUsernameNotFound_whenAbsent() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> customUserDetailsService.loadUserById(999L));
    }

    private User aUser(RoleName roleName) {
        Role role = new Role(roleName, roleName.name());
        return new User("Ada Lovelace", "ada@example.com", "hashed-password", role);
    }
}
