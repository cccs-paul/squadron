package com.squadron.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.dto.TenantDto;
import com.squadron.identity.exception.GlobalExceptionHandler;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TenantService tenantService;

    @InjectMocks
    private TenantController tenantController;

    private static final UUID TEST_TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tenantController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUpJwtSecurityContext(UUID tenantId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("tenant_id", tenantId.toString())
                .claim("sub", "test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void should_return201_when_createTenantSuccessful() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantDto created = TenantDto.builder()
                .id(tenantId)
                .name("Acme Corp")
                .slug("acme-corp")
                .status("active")
                .createdAt(Instant.now())
                .build();
        when(tenantService.createTenant(any(TenantDto.class))).thenReturn(created);

        TenantDto request = TenantDto.builder()
                .name("Acme Corp")
                .build();

        mockMvc.perform(post("/api/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Acme Corp"))
                .andExpect(jsonPath("$.data.slug").value("acme-corp"))
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    void should_returnTenants_when_listTenants() throws Exception {
        TenantDto t1 = TenantDto.builder().id(UUID.randomUUID()).name("Acme").slug("acme").build();
        TenantDto t2 = TenantDto.builder().id(UUID.randomUUID()).name("Beta").slug("beta").build();
        when(tenantService.listTenants()).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Acme"))
                .andExpect(jsonPath("$.data[1].name").value("Beta"));
    }

    @Test
    void should_returnTenant_when_getById() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantDto tenant = TenantDto.builder().id(tenantId).name("Acme").slug("acme").build();
        when(tenantService.getTenant(tenantId)).thenReturn(tenant);

        mockMvc.perform(get("/api/tenants/{id}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Acme"));
    }

    @Test
    void should_return404_when_tenantNotFound() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(tenantService.getTenant(tenantId)).thenThrow(new ResourceNotFoundException("Tenant", "id", tenantId));

        mockMvc.perform(get("/api/tenants/{id}", tenantId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_returnTenant_when_getBySlug() throws Exception {
        TenantDto tenant = TenantDto.builder().id(UUID.randomUUID()).name("Acme").slug("acme").build();
        when(tenantService.getTenantBySlug("acme")).thenReturn(tenant);

        mockMvc.perform(get("/api/tenants/slug/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value("acme"));
    }

    @Test
    void should_returnUpdatedTenant_when_updateSuccessful() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantDto updated = TenantDto.builder()
                .id(tenantId)
                .name("Updated Name")
                .slug("updated-name")
                .status("active")
                .build();
        when(tenantService.updateTenant(eq(tenantId), any(TenantDto.class))).thenReturn(updated);

        TenantDto request = TenantDto.builder().name("Updated Name").build();

        mockMvc.perform(put("/api/tenants/{id}", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Name"));
    }

    @Test
    void should_returnCurrentTenant_when_getCurrentTenant() throws Exception {
        setUpJwtSecurityContext(TEST_TENANT_ID);

        TenantDto tenant = TenantDto.builder()
                .id(TEST_TENANT_ID)
                .name("Current Org")
                .slug("current-org")
                .status("ACTIVE")
                .settings(Map.of("aiEnabled", true, "maxUsers", 25))
                .build();
        when(tenantService.getTenant(TEST_TENANT_ID)).thenReturn(tenant);

        mockMvc.perform(get("/api/tenants/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Current Org"))
                .andExpect(jsonPath("$.data.settings.aiEnabled").value(true))
                .andExpect(jsonPath("$.data.settings.maxUsers").value(25));
    }

    @Test
    void should_updateCurrentTenantSettings_when_patchRequest() throws Exception {
        setUpJwtSecurityContext(TEST_TENANT_ID);

        TenantDto updated = TenantDto.builder()
                .id(TEST_TENANT_ID)
                .name("Current Org")
                .slug("current-org")
                .status("ACTIVE")
                .settings(Map.of("aiEnabled", false, "maxUsers", 50))
                .build();
        when(tenantService.updateTenantSettings(eq(TEST_TENANT_ID), any(Map.class))).thenReturn(updated);

        String settingsJson = objectMapper.writeValueAsString(Map.of("aiEnabled", false, "maxUsers", 50));

        mockMvc.perform(patch("/api/tenants/current/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.settings.aiEnabled").value(false))
                .andExpect(jsonPath("$.data.settings.maxUsers").value(50));
    }

    @Test
    void should_return404_when_currentTenantNotFound() throws Exception {
        setUpJwtSecurityContext(TEST_TENANT_ID);

        when(tenantService.getTenant(TEST_TENANT_ID)).thenThrow(new ResourceNotFoundException("Tenant", "id", TEST_TENANT_ID));

        mockMvc.perform(get("/api/tenants/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
