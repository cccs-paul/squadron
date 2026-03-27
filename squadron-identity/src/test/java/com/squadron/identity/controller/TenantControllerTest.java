package com.squadron.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.dto.TenantDto;
import com.squadron.identity.exception.GlobalExceptionHandler;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tenantController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
}
