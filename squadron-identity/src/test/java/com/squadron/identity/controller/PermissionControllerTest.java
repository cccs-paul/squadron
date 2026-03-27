package com.squadron.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.dto.ResourcePermissionDto;
import com.squadron.common.security.AccessLevel;
import com.squadron.common.security.TenantContext;
import com.squadron.identity.exception.GlobalExceptionHandler;
import com.squadron.identity.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PermissionControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private PermissionController permissionController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(permissionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_return201_when_grantPermissionSuccessful() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();

        ResourcePermissionDto created = ResourcePermissionDto.builder()
                .id(permId)
                .tenantId(tenantId)
                .resourceType("PROJECT")
                .resourceId(resourceId)
                .granteeType("USER")
                .granteeId(granteeId)
                .accessLevel("WRITE")
                .build();
        when(permissionService.grantPermission(tenantId, "PROJECT", resourceId, "USER", granteeId, "WRITE"))
                .thenReturn(created);

        ResourcePermissionDto request = ResourcePermissionDto.builder()
                .tenantId(tenantId)
                .resourceType("PROJECT")
                .resourceId(resourceId)
                .granteeType("USER")
                .granteeId(granteeId)
                .accessLevel("WRITE")
                .build();

        mockMvc.perform(post("/api/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resourceType").value("PROJECT"))
                .andExpect(jsonPath("$.data.accessLevel").value("WRITE"));
    }

    @Test
    void should_return200_when_revokePermissionSuccessful() throws Exception {
        UUID permId = UUID.randomUUID();
        doNothing().when(permissionService).revokePermission(permId);

        mockMvc.perform(delete("/api/permissions/{id}", permId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(permissionService).revokePermission(permId);
    }

    @Test
    void should_returnPermissions_when_getPermissions() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        ResourcePermissionDto p1 = ResourcePermissionDto.builder()
                .id(UUID.randomUUID())
                .resourceType("PROJECT")
                .accessLevel("READ")
                .build();
        ResourcePermissionDto p2 = ResourcePermissionDto.builder()
                .id(UUID.randomUUID())
                .resourceType("PROJECT")
                .accessLevel("WRITE")
                .build();
        when(permissionService.getPermissions(tenantId, "PROJECT", resourceId)).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/permissions")
                        .param("tenantId", tenantId.toString())
                        .param("resourceType", "PROJECT")
                        .param("resourceId", resourceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_returnHasAccess_when_checkAccessWithContext() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        TenantContext.setContext(TenantContext.builder()
                .userId(userId)
                .tenantId(tenantId)
                .build());

        when(permissionService.checkAccess(userId, tenantId, "PROJECT", resourceId, AccessLevel.READ))
                .thenReturn(true);
        when(permissionService.getEffectiveAccessLevel(userId, tenantId, "PROJECT", resourceId))
                .thenReturn(AccessLevel.WRITE);

        mockMvc.perform(get("/api/permissions/check")
                        .param("resourceType", "PROJECT")
                        .param("resourceId", resourceId.toString())
                        .param("accessLevel", "READ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hasAccess").value(true))
                .andExpect(jsonPath("$.data.effectiveAccessLevel").value("WRITE"));
    }

    @Test
    void should_returnFalse_when_checkAccessWithoutContext() throws Exception {
        // No TenantContext set => userId and tenantId are null
        UUID resourceId = UUID.randomUUID();

        mockMvc.perform(get("/api/permissions/check")
                        .param("resourceType", "PROJECT")
                        .param("resourceId", resourceId.toString())
                        .param("accessLevel", "READ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hasAccess").value(false));
    }
}
