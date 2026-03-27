package com.squadron.identity.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantTest {

    @Test
    void should_buildTenant_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Tenant tenant = Tenant.builder()
                .id(id)
                .name("Acme Corp")
                .slug("acme-corp")
                .status("ACTIVE")
                .settings("{\"key\":\"value\"}")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, tenant.getId());
        assertEquals("Acme Corp", tenant.getName());
        assertEquals("acme-corp", tenant.getSlug());
        assertEquals("ACTIVE", tenant.getStatus());
        assertEquals("{\"key\":\"value\"}", tenant.getSettings());
        assertEquals(now, tenant.getCreatedAt());
        assertEquals(now, tenant.getUpdatedAt());
    }

    @Test
    void should_defaultStatusToActive_when_notSpecified() {
        Tenant tenant = Tenant.builder()
                .name("Test Org")
                .slug("test-org")
                .build();

        assertEquals("ACTIVE", tenant.getStatus());
    }

    @Test
    void should_allowOverridingDefaultStatus_when_specified() {
        Tenant tenant = Tenant.builder()
                .name("Test Org")
                .slug("test-org")
                .status("SUSPENDED")
                .build();

        assertEquals("SUSPENDED", tenant.getStatus());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        Tenant tenant = Tenant.builder()
                .name("Test Org")
                .slug("test-org")
                .build();

        assertNull(tenant.getCreatedAt());
        assertNull(tenant.getUpdatedAt());

        tenant.onCreate();

        assertNotNull(tenant.getCreatedAt());
        assertNotNull(tenant.getUpdatedAt());
        assertEquals(tenant.getCreatedAt(), tenant.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        Tenant tenant = Tenant.builder()
                .name("Test Org")
                .slug("test-org")
                .build();
        tenant.onCreate();
        Instant originalUpdatedAt = tenant.getUpdatedAt();

        // Small delay to ensure different timestamp
        Thread.sleep(10);
        tenant.onUpdate();

        assertEquals(tenant.getCreatedAt(), originalUpdatedAt);
        assertTrue(tenant.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    void should_createTenant_when_usingNoArgConstructor() {
        Tenant tenant = new Tenant();

        assertNull(tenant.getId());
        assertNull(tenant.getName());
        assertNull(tenant.getSlug());
        assertNull(tenant.getSettings());
        assertNull(tenant.getCreatedAt());
        assertNull(tenant.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        Tenant tenant = new Tenant();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        tenant.setId(id);
        tenant.setName("Updated");
        tenant.setSlug("updated");
        tenant.setStatus("SUSPENDED");
        tenant.setSettings("{\"updated\":true}");
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);

        assertEquals(id, tenant.getId());
        assertEquals("Updated", tenant.getName());
        assertEquals("updated", tenant.getSlug());
        assertEquals("SUSPENDED", tenant.getStatus());
        assertEquals("{\"updated\":true}", tenant.getSettings());
        assertEquals(now, tenant.getCreatedAt());
        assertEquals(now, tenant.getUpdatedAt());
    }

    @Test
    void should_beEqual_when_sameFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Tenant t1 = Tenant.builder().id(id).name("A").slug("a").status("ACTIVE").createdAt(now).updatedAt(now).build();
        Tenant t2 = Tenant.builder().id(id).name("A").slug("a").status("ACTIVE").createdAt(now).updatedAt(now).build();

        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentIds() {
        Tenant t1 = Tenant.builder().id(UUID.randomUUID()).name("A").slug("a").build();
        Tenant t2 = Tenant.builder().id(UUID.randomUUID()).name("A").slug("a").build();

        assertNotEquals(t1, t2);
    }

    @Test
    void should_generateToString_when_called() {
        Tenant tenant = Tenant.builder().name("Acme").slug("acme").build();

        String toString = tenant.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("Acme"));
        assertTrue(toString.contains("acme"));
    }

    @Test
    void should_createTenant_when_usingAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Tenant tenant = new Tenant(id, "All Args", "all-args", "ACTIVE", "{}", now, now);

        assertEquals(id, tenant.getId());
        assertEquals("All Args", tenant.getName());
        assertEquals("all-args", tenant.getSlug());
        assertEquals("ACTIVE", tenant.getStatus());
        assertEquals("{}", tenant.getSettings());
        assertEquals(now, tenant.getCreatedAt());
        assertEquals(now, tenant.getUpdatedAt());
    }
}
