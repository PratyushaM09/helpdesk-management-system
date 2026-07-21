package com.helpdesk.role.controller;

import com.helpdesk.constant.ApiConstants;
import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.role.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full request-lifecycle proof, matching {@code UserControllerIntegrationTest}'s
 * conventions exactly: real {@link RoleController}, real Service/Mapper/
 * Repository, real H2 database (test profile), real
 * {@code GlobalExceptionHandler}. {@code @Transactional} rolls back every
 * test method, so the three roles {@code RoleSeeder} seeds at application
 * startup are the only rows any test ever sees change.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleControllerIntegrationTest {

    private static final String BASE_URL = ApiConstants.API_BASE_PATH + "/roles";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    // --- GET /api/v1/roles ---

    @Test
    void getRoles_shouldReturnPagedEnvelope_withSeededRoles() throws Exception {
        mockMvc.perform(get(BASE_URL).param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void getRoles_shouldRespectPageSize_whenSizeIsSmallerThanTotal() throws Exception {
        mockMvc.perform(get(BASE_URL).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void getRoles_shouldSortByAllowedField_whenSortIsName() throws Exception {
        mockMvc.perform(get(BASE_URL).param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("ADMIN"));
    }

    @Test
    void getRoles_shouldReturn400_whenSortFieldIsNotAllowed() throws Exception {
        mockMvc.perform(get(BASE_URL).param("sort", "description,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    // --- GET /api/v1/roles/{id} ---

    @Test
    void getRole_shouldReturn200_whenRoleExists() throws Exception {
        Role role = aSeededRole(RoleName.ADMIN);

        mockMvc.perform(get(BASE_URL + "/{id}", role.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("ADMIN"))
                .andExpect(jsonPath("$.data.system").value(true));
    }

    @Test
    void getRole_shouldReturn404_whenRoleDoesNotExist() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // --- PUT /api/v1/roles/{id} ---

    @Test
    void updateRole_shouldReturn200AndPersistDescription_whenRequestIsValid() throws Exception {
        Role role = aSeededRole(RoleName.SUPPORT_ENGINEER);

        mockMvc.perform(put(BASE_URL + "/{id}", role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRoleJson("Updated support engineer description")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Updated support engineer description"))
                .andExpect(jsonPath("$.data.name").value("SUPPORT_ENGINEER"))
                .andExpect(jsonPath("$.data.system").value(true));

        Role persisted = roleRepository.findById(role.getId()).orElseThrow();
        assertEquals("Updated support engineer description", persisted.getDescription());
        assertEquals(RoleName.SUPPORT_ENGINEER, persisted.getName());
        assertTrue(persisted.isSystem());
    }

    @Test
    void updateRole_shouldReturn400_whenDescriptionIsBlank() throws Exception {
        Role role = aSeededRole(RoleName.USER);

        mockMvc.perform(put(BASE_URL + "/{id}", role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRoleJson("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void updateRole_shouldReturn400_whenDescriptionExceedsMaxLength() throws Exception {
        Role role = aSeededRole(RoleName.USER);
        String oversizedDescription = "a".repeat(256);

        mockMvc.perform(put(BASE_URL + "/{id}", role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRoleJson(oversizedDescription)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void updateRole_shouldReturn404_whenRoleDoesNotExist() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRoleJson("Updated description")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // --- DELETE /api/v1/roles/{id} ---

    @Test
    void deleteRole_shouldReturn409AndLeaveRoleIntact_whenRoleIsSystemRole() throws Exception {
        Role role = aSeededRole(RoleName.ADMIN);

        mockMvc.perform(delete(BASE_URL + "/{id}", role.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));

        Role stillPresent = roleRepository.findById(role.getId()).orElseThrow();
        assertEquals(RoleName.ADMIN, stillPresent.getName());
    }

    @Test
    void deleteRole_shouldReturn404_whenRoleDoesNotExist() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // --- helpers ---

    private Role aSeededRole(RoleName name) {
        return roleRepository.findByName(name).orElseThrow();
    }

    private String updateRoleJson(String description) {
        return """
                {"description": "%s"}
                """.formatted(description);
    }
}
