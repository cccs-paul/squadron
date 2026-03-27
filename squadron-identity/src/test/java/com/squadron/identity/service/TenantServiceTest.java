package com.squadron.identity.service;

import com.squadron.common.dto.TenantDto;
import com.squadron.identity.entity.Tenant;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantService tenantService;

    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder()
                .id(tenantId)
                .name("Test Org")
                .slug("test-org")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void should_createTenant_when_validDto() {
        TenantDto dto = TenantDto.builder().name("New Org").build();
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

        TenantDto result = tenantService.createTenant(dto);

        assertNotNull(result);
        assertEquals("Test Org", result.getName());
        assertEquals("test-org", result.getSlug());
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void should_createTenantWithDefaultStatus_when_statusIsNull() {
        TenantDto dto = TenantDto.builder().name("New Org").build();
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

        tenantService.createTenant(dto);

        verify(tenantRepository).save(argThat(t -> "ACTIVE".equals(t.getStatus())));
    }

    @Test
    void should_getTenant_when_exists() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        TenantDto result = tenantService.getTenant(tenantId);

        assertEquals(tenantId, result.getId());
        assertEquals("Test Org", result.getName());
    }

    @Test
    void should_throwNotFound_when_tenantDoesNotExist() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tenantService.getTenant(tenantId));
    }

    @Test
    void should_getTenantBySlug_when_exists() {
        when(tenantRepository.findBySlug("test-org")).thenReturn(Optional.of(tenant));

        TenantDto result = tenantService.getTenantBySlug("test-org");

        assertEquals("test-org", result.getSlug());
    }

    @Test
    void should_throwNotFound_when_slugDoesNotExist() {
        when(tenantRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tenantService.getTenantBySlug("missing"));
    }

    @Test
    void should_updateTenant_when_validDto() {
        TenantDto dto = TenantDto.builder()
                .name("Updated Org")
                .status("SUSPENDED")
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        TenantDto result = tenantService.updateTenant(tenantId, dto);

        assertNotNull(result);
        assertEquals("Updated Org", tenant.getName());
        assertEquals("SUSPENDED", tenant.getStatus());
    }

    @Test
    void should_updateOnlyName_when_statusIsNull() {
        TenantDto dto = TenantDto.builder().name("New Name").build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        tenantService.updateTenant(tenantId, dto);

        assertEquals("New Name", tenant.getName());
        assertEquals("ACTIVE", tenant.getStatus()); // unchanged
    }

    @Test
    void should_throwNotFound_when_updatingNonExistentTenant() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                tenantService.updateTenant(tenantId, new TenantDto()));
    }

    @Test
    void should_listTenants_when_called() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        List<TenantDto> results = tenantService.listTenants();

        assertEquals(1, results.size());
        assertEquals("test-org", results.get(0).getSlug());
    }
}
