package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        Map<String, Object> settings = Map.of("plan", "enterprise", "maxUsers", 500);
        Instant now = Instant.now();

        TenantDto dto = TenantDto.builder()
                .id(id)
                .name("Acme Corporation")
                .slug("acme-corp")
                .status("ACTIVE")
                .settings(settings)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals("Acme Corporation", dto.getName());
        assertEquals("acme-corp", dto.getSlug());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(settings, dto.getSettings());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        TenantDto dto = new TenantDto();

        assertNull(dto.getId());
        assertNull(dto.getName());
        assertNull(dto.getSlug());
        assertNull(dto.getStatus());
        assertNull(dto.getSettings());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        Map<String, Object> settings = Map.of("feature_flags", Map.of("beta", true));
        Instant created = Instant.now();
        Instant updated = Instant.now();

        TenantDto dto = new TenantDto(
                id, "Globex Inc", "globex", "SUSPENDED", settings, created, updated
        );

        assertEquals(id, dto.getId());
        assertEquals("Globex Inc", dto.getName());
        assertEquals("globex", dto.getSlug());
        assertEquals("SUSPENDED", dto.getStatus());
        assertEquals(settings, dto.getSettings());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(updated, dto.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        TenantDto dto = new TenantDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setName("New Tenant");
        dto.setSlug("new-tenant");
        dto.setStatus("PROVISIONING");

        assertEquals(id, dto.getId());
        assertEquals("New Tenant", dto.getName());
        assertEquals("new-tenant", dto.getSlug());
        assertEquals("PROVISIONING", dto.getStatus());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();

        TenantDto dto1 = TenantDto.builder()
                .id(id)
                .name("Tenant")
                .slug("tenant")
                .status("ACTIVE")
                .build();

        TenantDto dto2 = TenantDto.builder()
                .id(id)
                .name("Tenant")
                .slug("tenant")
                .status("ACTIVE")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        TenantDto dto1 = TenantDto.builder()
                .slug("tenant-a")
                .build();

        TenantDto dto2 = TenantDto.builder()
                .slug("tenant-b")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        TenantDto dto = TenantDto.builder()
                .name("Acme Corp")
                .slug("acme")
                .status("ACTIVE")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("Acme Corp"));
        assertTrue(str.contains("acme"));
        assertTrue(str.contains("ACTIVE"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        TenantDto dto = TenantDto.builder()
                .id(null)
                .name(null)
                .slug(null)
                .status(null)
                .settings(null)
                .createdAt(null)
                .updatedAt(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getName());
        assertNull(dto.getSlug());
        assertNull(dto.getStatus());
        assertNull(dto.getSettings());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_handleEmptySettings_when_emptyMapProvided() {
        TenantDto dto = TenantDto.builder()
                .settings(Map.of())
                .build();

        assertNotNull(dto.getSettings());
        assertTrue(dto.getSettings().isEmpty());
    }
}
