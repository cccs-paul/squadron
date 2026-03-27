package com.squadron.common.security;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationResultTest {

    @Test
    void should_createWithNoArgs_when_defaultConstructorUsed() {
        AuthenticationResult result = new AuthenticationResult();

        assertNull(result.getExternalId());
        assertNull(result.getEmail());
        assertNull(result.getDisplayName());
        assertNull(result.getRoles());
        assertNull(result.getAuthProvider());
        assertNull(result.getAttributes());
        assertNull(result.getUserId());
        assertNull(result.getTenantId());
    }

    @Test
    void should_setAllFields_when_allArgsConstructorUsed() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Set<String> roles = Set.of("developer", "qa");
        Map<String, Object> attrs = Map.of("department", "engineering");

        AuthenticationResult result = new AuthenticationResult(
                "ext-123", "user@example.com", "John Doe",
                roles, "oidc", attrs, userId, tenantId
        );

        assertEquals("ext-123", result.getExternalId());
        assertEquals("user@example.com", result.getEmail());
        assertEquals("John Doe", result.getDisplayName());
        assertEquals(roles, result.getRoles());
        assertEquals("oidc", result.getAuthProvider());
        assertEquals(attrs, result.getAttributes());
        assertEquals(userId, result.getUserId());
        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void should_buildWithBuilder_when_builderUsed() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthenticationResult result = AuthenticationResult.builder()
                .externalId("ldap-dn")
                .email("admin@corp.com")
                .displayName("Admin User")
                .roles(Set.of("squadron-admin"))
                .authProvider("ldap")
                .attributes(Map.of("title", "Admin"))
                .userId(userId)
                .tenantId(tenantId)
                .build();

        assertEquals("ldap-dn", result.getExternalId());
        assertEquals("admin@corp.com", result.getEmail());
        assertEquals("Admin User", result.getDisplayName());
        assertTrue(result.getRoles().contains("squadron-admin"));
        assertEquals("ldap", result.getAuthProvider());
        assertEquals("Admin", result.getAttributes().get("title"));
        assertEquals(userId, result.getUserId());
        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void should_allowSettingFields_when_settersUsed() {
        AuthenticationResult result = new AuthenticationResult();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        result.setExternalId("sub-456");
        result.setEmail("test@test.com");
        result.setDisplayName("Test User");
        result.setRoles(Set.of("viewer"));
        result.setAuthProvider("keycloak");
        result.setAttributes(Map.of("org", "acme"));
        result.setUserId(userId);
        result.setTenantId(tenantId);

        assertEquals("sub-456", result.getExternalId());
        assertEquals("test@test.com", result.getEmail());
        assertEquals("Test User", result.getDisplayName());
        assertEquals(Set.of("viewer"), result.getRoles());
        assertEquals("keycloak", result.getAuthProvider());
        assertEquals(Map.of("org", "acme"), result.getAttributes());
        assertEquals(userId, result.getUserId());
        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthenticationResult r1 = AuthenticationResult.builder()
                .externalId("id1").email("a@b.com").userId(userId).tenantId(tenantId).build();
        AuthenticationResult r2 = AuthenticationResult.builder()
                .externalId("id1").email("a@b.com").userId(userId).tenantId(tenantId).build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        AuthenticationResult r1 = AuthenticationResult.builder().externalId("id1").build();
        AuthenticationResult r2 = AuthenticationResult.builder().externalId("id2").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_returnStringRepresentation_when_toStringCalled() {
        AuthenticationResult result = AuthenticationResult.builder()
                .email("user@example.com")
                .authProvider("oidc")
                .build();

        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("user@example.com"));
        assertTrue(str.contains("oidc"));
    }
}
