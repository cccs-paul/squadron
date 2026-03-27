package com.squadron.identity.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityGroupTest {

    @Test
    void should_buildSecurityGroup_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        SecurityGroup group = SecurityGroup.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Admins")
                .description("Administrator group")
                .accessLevel("ADMIN")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, group.getId());
        assertEquals(tenantId, group.getTenantId());
        assertEquals("Admins", group.getName());
        assertEquals("Administrator group", group.getDescription());
        assertEquals("ADMIN", group.getAccessLevel());
        assertEquals(now, group.getCreatedAt());
        assertEquals(now, group.getUpdatedAt());
    }

    @Test
    void should_defaultAccessLevelToRead_when_notSpecified() {
        SecurityGroup group = SecurityGroup.builder()
                .tenantId(UUID.randomUUID())
                .name("Viewers")
                .build();

        assertEquals("READ", group.getAccessLevel());
    }

    @Test
    void should_allowOverridingDefaultAccessLevel_when_specified() {
        SecurityGroup group = SecurityGroup.builder()
                .tenantId(UUID.randomUUID())
                .name("Writers")
                .accessLevel("WRITE")
                .build();

        assertEquals("WRITE", group.getAccessLevel());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        SecurityGroup group = SecurityGroup.builder()
                .name("Test Group")
                .tenantId(UUID.randomUUID())
                .build();

        assertNull(group.getCreatedAt());
        assertNull(group.getUpdatedAt());

        group.onCreate();

        assertNotNull(group.getCreatedAt());
        assertNotNull(group.getUpdatedAt());
        assertEquals(group.getCreatedAt(), group.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        SecurityGroup group = SecurityGroup.builder().name("Test").build();
        group.onCreate();
        Instant originalUpdatedAt = group.getUpdatedAt();

        Thread.sleep(10);
        group.onUpdate();

        assertEquals(group.getCreatedAt(), originalUpdatedAt);
        assertTrue(group.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    void should_createSecurityGroup_when_usingNoArgConstructor() {
        SecurityGroup group = new SecurityGroup();

        assertNull(group.getId());
        assertNull(group.getTenantId());
        assertNull(group.getName());
        assertNull(group.getDescription());
        assertNull(group.getCreatedAt());
        assertNull(group.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        SecurityGroup group = new SecurityGroup();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        group.setId(id);
        group.setTenantId(tenantId);
        group.setName("Editors");
        group.setDescription("Editor group");
        group.setAccessLevel("WRITE");

        assertEquals(id, group.getId());
        assertEquals(tenantId, group.getTenantId());
        assertEquals("Editors", group.getName());
        assertEquals("Editor group", group.getDescription());
        assertEquals("WRITE", group.getAccessLevel());
    }

    @Test
    void should_beEqual_when_sameFields() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        SecurityGroup g1 = SecurityGroup.builder().id(id).tenantId(tenantId).name("G").accessLevel("READ").build();
        SecurityGroup g2 = SecurityGroup.builder().id(id).tenantId(tenantId).name("G").accessLevel("READ").build();

        assertEquals(g1, g2);
        assertEquals(g1.hashCode(), g2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentIds() {
        SecurityGroup g1 = SecurityGroup.builder().id(UUID.randomUUID()).name("G").build();
        SecurityGroup g2 = SecurityGroup.builder().id(UUID.randomUUID()).name("G").build();

        assertNotEquals(g1, g2);
    }

    @Test
    void should_generateToString_when_called() {
        SecurityGroup group = SecurityGroup.builder().name("Admins").description("Admin group").build();

        String toString = group.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("Admins"));
    }

    @Test
    void should_handleNullDescription_when_notSet() {
        SecurityGroup group = SecurityGroup.builder()
                .tenantId(UUID.randomUUID())
                .name("No Desc")
                .build();

        assertNull(group.getDescription());
    }
}
