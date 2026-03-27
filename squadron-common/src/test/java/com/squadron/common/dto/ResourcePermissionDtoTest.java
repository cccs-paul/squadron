package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePermissionDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();

        ResourcePermissionDto dto = ResourcePermissionDto.builder()
                .id(id)
                .tenantId(tenantId)
                .resourceType("PROJECT")
                .resourceId(resourceId)
                .granteeType("USER")
                .granteeId(granteeId)
                .accessLevel("WRITE")
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("PROJECT", dto.getResourceType());
        assertEquals(resourceId, dto.getResourceId());
        assertEquals("USER", dto.getGranteeType());
        assertEquals(granteeId, dto.getGranteeId());
        assertEquals("WRITE", dto.getAccessLevel());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        ResourcePermissionDto dto = new ResourcePermissionDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getResourceType());
        assertNull(dto.getResourceId());
        assertNull(dto.getGranteeType());
        assertNull(dto.getGranteeId());
        assertNull(dto.getAccessLevel());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();

        ResourcePermissionDto dto = new ResourcePermissionDto(
                id, tenantId, "TASK", resourceId, "TEAM", granteeId, "READ"
        );

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("TASK", dto.getResourceType());
        assertEquals(resourceId, dto.getResourceId());
        assertEquals("TEAM", dto.getGranteeType());
        assertEquals(granteeId, dto.getGranteeId());
        assertEquals("READ", dto.getAccessLevel());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        ResourcePermissionDto dto = new ResourcePermissionDto();
        UUID id = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        dto.setId(id);
        dto.setResourceType("REPOSITORY");
        dto.setResourceId(resourceId);
        dto.setGranteeType("SECURITY_GROUP");
        dto.setAccessLevel("ADMIN");

        assertEquals(id, dto.getId());
        assertEquals("REPOSITORY", dto.getResourceType());
        assertEquals(resourceId, dto.getResourceId());
        assertEquals("SECURITY_GROUP", dto.getGranteeType());
        assertEquals("ADMIN", dto.getAccessLevel());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();

        ResourcePermissionDto dto1 = ResourcePermissionDto.builder()
                .id(id)
                .tenantId(tenantId)
                .resourceType("PROJECT")
                .resourceId(resourceId)
                .granteeType("USER")
                .granteeId(granteeId)
                .accessLevel("READ")
                .build();

        ResourcePermissionDto dto2 = ResourcePermissionDto.builder()
                .id(id)
                .tenantId(tenantId)
                .resourceType("PROJECT")
                .resourceId(resourceId)
                .granteeType("USER")
                .granteeId(granteeId)
                .accessLevel("READ")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ResourcePermissionDto dto1 = ResourcePermissionDto.builder()
                .accessLevel("READ")
                .build();

        ResourcePermissionDto dto2 = ResourcePermissionDto.builder()
                .accessLevel("ADMIN")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        ResourcePermissionDto dto = ResourcePermissionDto.builder()
                .resourceType("CONFIGURATION")
                .granteeType("TEAM")
                .accessLevel("WRITE")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("CONFIGURATION"));
        assertTrue(str.contains("TEAM"));
        assertTrue(str.contains("WRITE"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        ResourcePermissionDto dto = ResourcePermissionDto.builder()
                .id(null)
                .tenantId(null)
                .resourceType(null)
                .resourceId(null)
                .granteeType(null)
                .granteeId(null)
                .accessLevel(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getResourceType());
        assertNull(dto.getResourceId());
        assertNull(dto.getGranteeType());
        assertNull(dto.getGranteeId());
        assertNull(dto.getAccessLevel());
    }
}
