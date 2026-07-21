package com.helpdesk.role.mapper;

import com.helpdesk.role.dto.response.RoleResponse;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real {@link RoleMapperImpl}, unlike {@code RoleServiceImplTest}
 * which mocks {@link RoleMapper} — this is the one place the actual
 * field-by-field mapping is verified. {@code id}/{@code createdAt}/
 * {@code updatedAt} have no setters (JPA-assigned only), so
 * {@link ReflectionTestUtils} populates them directly for the assertion;
 * no database, no Spring context.
 */
class RoleMapperImplTest {

    private RoleMapperImpl roleMapper;

    @BeforeEach
    void setUp() {
        roleMapper = new RoleMapperImpl();
    }

    @Test
    void toResponse_shouldMapEveryField_forSystemRole() {
        Role role = new Role(RoleName.ADMIN, "Platform owner - manages users, roles, and categories.", true);
        ReflectionTestUtils.setField(role, "id", 3L);
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");
        ReflectionTestUtils.setField(role, "createdAt", createdAt);
        ReflectionTestUtils.setField(role, "updatedAt", updatedAt);

        RoleResponse response = roleMapper.toResponse(role);

        assertEquals(3L, response.id());
        assertEquals(RoleName.ADMIN, response.name());
        assertEquals("Platform owner - manages users, roles, and categories.", response.description());
        assertTrue(response.system());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());
    }

    @Test
    void toResponse_shouldMapSystemFalse_forNonSystemRole() {
        Role role = new Role(RoleName.SUPPORT_ENGINEER, "Custom support role", false);
        ReflectionTestUtils.setField(role, "id", 7L);

        RoleResponse response = roleMapper.toResponse(role);

        assertEquals(7L, response.id());
        assertEquals(RoleName.SUPPORT_ENGINEER, response.name());
        assertEquals("Custom support role", response.description());
        assertFalse(response.system());
    }
}
