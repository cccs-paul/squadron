package com.squadron.identity.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void should_buildUser_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();
        Set<String> roles = Set.of("ADMIN", "DEVELOPER");

        User user = User.builder()
                .id(id)
                .tenantId(tenantId)
                .externalId("ext-123")
                .email("user@example.com")
                .displayName("Test User")
                .role("ADMIN")
                .authProvider("oidc")
                .roles(roles)
                .settings("{\"theme\":\"dark\"}")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, user.getId());
        assertEquals(tenantId, user.getTenantId());
        assertEquals("ext-123", user.getExternalId());
        assertEquals("user@example.com", user.getEmail());
        assertEquals("Test User", user.getDisplayName());
        assertEquals("ADMIN", user.getRole());
        assertEquals("oidc", user.getAuthProvider());
        assertEquals(roles, user.getRoles());
        assertEquals("{\"theme\":\"dark\"}", user.getSettings());
        assertEquals(now, user.getCreatedAt());
        assertEquals(now, user.getUpdatedAt());
    }

    @Test
    void should_defaultRoleToDeveloper_when_notSpecified() {
        User user = User.builder()
                .tenantId(UUID.randomUUID())
                .externalId("ext-1")
                .email("test@example.com")
                .build();

        assertEquals("DEVELOPER", user.getRole());
    }

    @Test
    void should_defaultAuthProviderToLdap_when_notSpecified() {
        User user = User.builder()
                .tenantId(UUID.randomUUID())
                .externalId("ext-1")
                .email("test@example.com")
                .build();

        assertEquals("ldap", user.getAuthProvider());
    }

    @Test
    void should_allowOverridingDefaults_when_specified() {
        User user = User.builder()
                .role("ADMIN")
                .authProvider("oidc")
                .build();

        assertEquals("ADMIN", user.getRole());
        assertEquals("oidc", user.getAuthProvider());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        User user = User.builder()
                .tenantId(UUID.randomUUID())
                .externalId("ext-1")
                .email("test@example.com")
                .build();

        assertNull(user.getCreatedAt());
        assertNull(user.getUpdatedAt());

        user.onCreate();

        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        User user = User.builder().build();
        user.onCreate();
        Instant originalUpdatedAt = user.getUpdatedAt();

        Thread.sleep(10);
        user.onUpdate();

        assertTrue(user.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    @SuppressWarnings("deprecation")
    void should_returnExternalId_when_getKeycloakIdCalled() {
        User user = User.builder()
                .externalId("ext-abc-123")
                .build();

        assertEquals("ext-abc-123", user.getKeycloakId());
    }

    @Test
    @SuppressWarnings("deprecation")
    void should_setExternalId_when_setKeycloakIdCalled() {
        User user = new User();
        user.setKeycloakId("keycloak-id-456");

        assertEquals("keycloak-id-456", user.getExternalId());
        assertEquals("keycloak-id-456", user.getKeycloakId());
    }

    @Test
    void should_createUser_when_usingNoArgConstructor() {
        User user = new User();

        assertNull(user.getId());
        assertNull(user.getTenantId());
        assertNull(user.getExternalId());
        assertNull(user.getEmail());
        assertNull(user.getDisplayName());
        assertNull(user.getRoles());
        assertNull(user.getSettings());
        assertNull(user.getCreatedAt());
        assertNull(user.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        User user = new User();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        user.setId(id);
        user.setTenantId(tenantId);
        user.setExternalId("ext-set");
        user.setEmail("setter@example.com");
        user.setDisplayName("Setter User");
        user.setRole("REVIEWER");
        user.setAuthProvider("saml");
        user.setSettings("{\"pref\":\"light\"}");

        assertEquals(id, user.getId());
        assertEquals(tenantId, user.getTenantId());
        assertEquals("ext-set", user.getExternalId());
        assertEquals("setter@example.com", user.getEmail());
        assertEquals("Setter User", user.getDisplayName());
        assertEquals("REVIEWER", user.getRole());
        assertEquals("saml", user.getAuthProvider());
        assertEquals("{\"pref\":\"light\"}", user.getSettings());
    }

    @Test
    void should_beEqual_when_sameFields() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User u1 = User.builder().id(id).tenantId(tenantId).externalId("ext").email("a@b.com").role("DEVELOPER").authProvider("ldap").build();
        User u2 = User.builder().id(id).tenantId(tenantId).externalId("ext").email("a@b.com").role("DEVELOPER").authProvider("ldap").build();

        assertEquals(u1, u2);
        assertEquals(u1.hashCode(), u2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentIds() {
        User u1 = User.builder().id(UUID.randomUUID()).email("a@b.com").build();
        User u2 = User.builder().id(UUID.randomUUID()).email("a@b.com").build();

        assertNotEquals(u1, u2);
    }

    @Test
    void should_generateToString_when_called() {
        User user = User.builder().email("test@example.com").displayName("Test").build();

        String toString = user.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("test@example.com"));
        assertTrue(toString.contains("Test"));
    }
}
