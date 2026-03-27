package com.squadron.identity.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePermissionTest {

    @Test
    void should_buildResourcePermission_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        Instant now = Instant.now();

        ResourcePermission perm = ResourcePermission.builder()
                .id(id)
                .tenantId(tenantId)
                .resourceType("REPOSITORY")
                .resourceId(resourceId)
                .granteeType("USER")
                .granteeId(granteeId)
                .accessLevel("WRITE")
                .createdAt(now)
                .build();

        assertEquals(id, perm.getId());
        assertEquals(tenantId, perm.getTenantId());
        assertEquals("REPOSITORY", perm.getResourceType());
        assertEquals(resourceId, perm.getResourceId());
        assertEquals("USER", perm.getGranteeType());
        assertEquals(granteeId, perm.getGranteeId());
        assertEquals("WRITE", perm.getAccessLevel());
        assertEquals(now, perm.getCreatedAt());
    }

    @Test
    void should_defaultAccessLevelToRead_when_notSpecified() {
        ResourcePermission perm = ResourcePermission.builder()
                .tenantId(UUID.randomUUID())
                .resourceType("REPOSITORY")
                .resourceId(UUID.randomUUID())
                .granteeType("USER")
                .granteeId(UUID.randomUUID())
                .build();

        assertEquals("READ", perm.getAccessLevel());
    }

    @Test
    void should_allowOverridingDefaultAccessLevel_when_specified() {
        ResourcePermission perm = ResourcePermission.builder()
                .accessLevel("ADMIN")
                .build();

        assertEquals("ADMIN", perm.getAccessLevel());
    }

    @Test
    void should_setCreatedAt_when_onCreateCalled() {
        ResourcePermission perm = ResourcePermission.builder()
                .tenantId(UUID.randomUUID())
                .resourceType("PROJECT")
                .resourceId(UUID.randomUUID())
                .granteeType("TEAM")
                .granteeId(UUID.randomUUID())
                .build();

        assertNull(perm.getCreatedAt());

        perm.onCreate();

        assertNotNull(perm.getCreatedAt());
    }

    @Test
    void should_createResourcePermission_when_usingNoArgConstructor() {
        ResourcePermission perm = new ResourcePermission();

        assertNull(perm.getId());
        assertNull(perm.getTenantId());
        assertNull(perm.getResourceType());
        assertNull(perm.getResourceId());
        assertNull(perm.getGranteeType());
        assertNull(perm.getGranteeId());
        assertNull(perm.getCreatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ResourcePermission perm = new ResourcePermission();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        Instant now = Instant.now();

        perm.setId(id);
        perm.setTenantId(tenantId);
        perm.setResourceType("TASK");
        perm.setResourceId(resourceId);
        perm.setGranteeType("GROUP");
        perm.setGranteeId(granteeId);
        perm.setAccessLevel("ADMIN");
        perm.setCreatedAt(now);

        assertEquals(id, perm.getId());
        assertEquals(tenantId, perm.getTenantId());
        assertEquals("TASK", perm.getResourceType());
        assertEquals(resourceId, perm.getResourceId());
        assertEquals("GROUP", perm.getGranteeType());
        assertEquals(granteeId, perm.getGranteeId());
        assertEquals("ADMIN", perm.getAccessLevel());
        assertEquals(now, perm.getCreatedAt());
    }

    @Test
    void should_beEqual_when_sameFields() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();

        ResourcePermission p1 = ResourcePermission.builder()
                .id(id).tenantId(tenantId).resourceType("REPO").resourceId(resourceId)
                .granteeType("USER").granteeId(granteeId).accessLevel("READ").build();
        ResourcePermission p2 = ResourcePermission.builder()
                .id(id).tenantId(tenantId).resourceType("REPO").resourceId(resourceId)
                .granteeType("USER").granteeId(granteeId).accessLevel("READ").build();

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentIds() {
        ResourcePermission p1 = ResourcePermission.builder().id(UUID.randomUUID()).resourceType("REPO").build();
        ResourcePermission p2 = ResourcePermission.builder().id(UUID.randomUUID()).resourceType("REPO").build();

        assertNotEquals(p1, p2);
    }

    @Test
    void should_generateToString_when_called() {
        ResourcePermission perm = ResourcePermission.builder()
                .resourceType("REPOSITORY")
                .granteeType("USER")
                .accessLevel("WRITE")
                .build();

        String toString = perm.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("REPOSITORY"));
        assertTrue(toString.contains("USER"));
        assertTrue(toString.contains("WRITE"));
    }

    @Test
    void should_createResourcePermission_when_usingAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        Instant now = Instant.now();

        ResourcePermission perm = new ResourcePermission(
                id, tenantId, "PROJECT", resourceId, "TEAM", granteeId, "ADMIN", now);

        assertEquals(id, perm.getId());
        assertEquals("PROJECT", perm.getResourceType());
        assertEquals("TEAM", perm.getGranteeType());
        assertEquals("ADMIN", perm.getAccessLevel());
    }
}
