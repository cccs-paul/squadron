package com.squadron.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_setAndGetContext_when_contextIsSet() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Set<String> roles = Set.of("developer", "qa");

        TenantContext context = TenantContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .email("test@example.com")
                .roles(roles)
                .authProvider("ldap")
                .build();

        TenantContext.setContext(context);

        assertEquals(tenantId, TenantContext.getTenantId());
        assertEquals(userId, TenantContext.getUserId());
        assertEquals("test@example.com", TenantContext.getEmail());
        assertEquals(roles, TenantContext.getRoles());
        assertEquals("ldap", TenantContext.getAuthProvider());
    }

    @Test
    void should_returnNull_when_noContextIsSet() {
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getUserId());
        assertNull(TenantContext.getEmail());
        assertNull(TenantContext.getAuthProvider());
    }

    @Test
    void should_returnEmptyRoles_when_noContextIsSet() {
        assertTrue(TenantContext.getRoles().isEmpty());
    }

    @Test
    void should_returnEmptyRoles_when_contextHasNullRoles() {
        TenantContext context = TenantContext.builder()
                .tenantId(UUID.randomUUID())
                .roles(null)
                .build();
        TenantContext.setContext(context);

        assertTrue(TenantContext.getRoles().isEmpty());
    }

    @Test
    void should_clearContext_when_clearIsCalled() {
        TenantContext context = TenantContext.builder()
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .build();
        TenantContext.setContext(context);

        assertNotNull(TenantContext.getTenantId());

        TenantContext.clear();

        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getContext());
    }

    @Test
    void should_returnTrue_when_hasRoleCalledWithExistingRole() {
        TenantContext context = TenantContext.builder()
                .roles(Set.of("developer", SecurityConstants.ROLE_ADMIN))
                .build();
        TenantContext.setContext(context);

        assertTrue(TenantContext.hasRole("developer"));
        assertTrue(TenantContext.hasRole(SecurityConstants.ROLE_ADMIN));
    }

    @Test
    void should_returnFalse_when_hasRoleCalledWithMissingRole() {
        TenantContext context = TenantContext.builder()
                .roles(Set.of("developer"))
                .build();
        TenantContext.setContext(context);

        assertFalse(TenantContext.hasRole("admin"));
    }

    @Test
    void should_returnTrue_when_isAdminAndUserHasAdminRole() {
        TenantContext context = TenantContext.builder()
                .roles(Set.of(SecurityConstants.ROLE_ADMIN))
                .build();
        TenantContext.setContext(context);

        assertTrue(TenantContext.isAdmin());
    }

    @Test
    void should_returnFalse_when_isAdminAndUserDoesNotHaveAdminRole() {
        TenantContext context = TenantContext.builder()
                .roles(Set.of("developer"))
                .build();
        TenantContext.setContext(context);

        assertFalse(TenantContext.isAdmin());
    }

    @Test
    void should_returnFalse_when_isAdminAndNoContextSet() {
        assertFalse(TenantContext.isAdmin());
    }

    @Test
    void should_returnFullContext_when_getContextCalled() {
        UUID tenantId = UUID.randomUUID();
        TenantContext context = TenantContext.builder()
                .tenantId(tenantId)
                .email("test@example.com")
                .build();
        TenantContext.setContext(context);

        TenantContext retrieved = TenantContext.getContext();
        assertNotNull(retrieved);
        assertEquals(tenantId, retrieved.getTenantId());
        assertEquals("test@example.com", retrieved.getEmail());
    }
}
