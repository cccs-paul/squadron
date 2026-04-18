package com.squadron.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantScopedLookupTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_useTenantScopedLookup_when_tenantContextPresent() {
        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder()
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .roles(Set.of("DEVELOPER"))
                .build());

        String result = TenantScopedLookup.findByIdScoped(
                entityId,
                id -> Optional.of("unscoped"),
                (id, tid) -> {
                    assertEquals(entityId, id);
                    assertEquals(tenantId, tid);
                    return Optional.of("scoped");
                },
                () -> new RuntimeException("not found"));

        assertEquals("scoped", result);
    }

    @Test
    void should_fallBackToUnscopedLookup_when_noTenantContext() {
        UUID entityId = UUID.randomUUID();

        String result = TenantScopedLookup.findByIdScoped(
                entityId,
                id -> Optional.of("unscoped"),
                (id, tid) -> Optional.of("scoped"),
                () -> new RuntimeException("not found"));

        assertEquals("unscoped", result);
    }

    @Test
    void should_throwException_when_entityNotFoundInScopedLookup() {
        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder()
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .roles(Set.of("DEVELOPER"))
                .build());

        assertThrows(RuntimeException.class, () ->
                TenantScopedLookup.findByIdScoped(
                        entityId,
                        id -> Optional.of("unscoped"),
                        (id, tid) -> Optional.empty(),
                        () -> new RuntimeException("not found")));
    }

    @Test
    void should_throwException_when_entityNotFoundInUnscopedLookup() {
        UUID entityId = UUID.randomUUID();

        assertThrows(RuntimeException.class, () ->
                TenantScopedLookup.findByIdScoped(
                        entityId,
                        id -> Optional.empty(),
                        (id, tid) -> Optional.empty(),
                        () -> new RuntimeException("not found")));
    }
}
