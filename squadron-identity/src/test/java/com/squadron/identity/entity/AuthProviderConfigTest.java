package com.squadron.identity.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthProviderConfigTest {

    @Test
    void should_buildAuthProviderConfig_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        AuthProviderConfig config = AuthProviderConfig.builder()
                .id(id)
                .tenantId(tenantId)
                .providerType("OIDC")
                .name("Corporate SSO")
                .enabled(true)
                .priority(10)
                .config("{\"issuer\":\"https://auth.example.com\"}")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, config.getId());
        assertEquals(tenantId, config.getTenantId());
        assertEquals("OIDC", config.getProviderType());
        assertEquals("Corporate SSO", config.getName());
        assertTrue(config.isEnabled());
        assertEquals(10, config.getPriority());
        assertEquals("{\"issuer\":\"https://auth.example.com\"}", config.getConfig());
        assertEquals(now, config.getCreatedAt());
        assertEquals(now, config.getUpdatedAt());
    }

    @Test
    void should_defaultEnabledToTrue_when_notSpecified() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .tenantId(UUID.randomUUID())
                .providerType("LDAP")
                .name("LDAP Provider")
                .config("{}")
                .build();

        assertTrue(config.isEnabled());
    }

    @Test
    void should_defaultPriorityToZero_when_notSpecified() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .tenantId(UUID.randomUUID())
                .providerType("SAML")
                .name("SAML Provider")
                .config("{}")
                .build();

        assertEquals(0, config.getPriority());
    }

    @Test
    void should_allowOverridingDefaults_when_specified() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .enabled(false)
                .priority(5)
                .build();

        assertFalse(config.isEnabled());
        assertEquals(5, config.getPriority());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .tenantId(UUID.randomUUID())
                .providerType("OIDC")
                .name("Test")
                .config("{}")
                .build();

        assertNull(config.getCreatedAt());
        assertNull(config.getUpdatedAt());

        config.onCreate();

        assertNotNull(config.getCreatedAt());
        assertNotNull(config.getUpdatedAt());
        assertEquals(config.getCreatedAt(), config.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        AuthProviderConfig config = AuthProviderConfig.builder().build();
        config.onCreate();
        Instant originalUpdatedAt = config.getUpdatedAt();

        Thread.sleep(10);
        config.onUpdate();

        assertEquals(config.getCreatedAt(), originalUpdatedAt);
        assertTrue(config.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    void should_createAuthProviderConfig_when_usingNoArgConstructor() {
        AuthProviderConfig config = new AuthProviderConfig();

        assertNull(config.getId());
        assertNull(config.getTenantId());
        assertNull(config.getProviderType());
        assertNull(config.getName());
        assertNull(config.getConfig());
        assertNull(config.getCreatedAt());
        assertNull(config.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        AuthProviderConfig config = new AuthProviderConfig();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        config.setId(id);
        config.setTenantId(tenantId);
        config.setProviderType("SAML");
        config.setName("SAML Auth");
        config.setEnabled(false);
        config.setPriority(3);
        config.setConfig("{\"endpoint\":\"https://saml.example.com\"}");

        assertEquals(id, config.getId());
        assertEquals(tenantId, config.getTenantId());
        assertEquals("SAML", config.getProviderType());
        assertEquals("SAML Auth", config.getName());
        assertFalse(config.isEnabled());
        assertEquals(3, config.getPriority());
        assertEquals("{\"endpoint\":\"https://saml.example.com\"}", config.getConfig());
    }

    @Test
    void should_beEqual_when_sameFields() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthProviderConfig c1 = AuthProviderConfig.builder()
                .id(id).tenantId(tenantId).providerType("OIDC").name("SSO").enabled(true).priority(0).config("{}").build();
        AuthProviderConfig c2 = AuthProviderConfig.builder()
                .id(id).tenantId(tenantId).providerType("OIDC").name("SSO").enabled(true).priority(0).config("{}").build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentIds() {
        AuthProviderConfig c1 = AuthProviderConfig.builder().id(UUID.randomUUID()).name("A").build();
        AuthProviderConfig c2 = AuthProviderConfig.builder().id(UUID.randomUUID()).name("A").build();

        assertNotEquals(c1, c2);
    }

    @Test
    void should_generateToString_when_called() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .providerType("OIDC")
                .name("Corporate SSO")
                .build();

        String toString = config.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("OIDC"));
        assertTrue(toString.contains("Corporate SSO"));
    }

    @Test
    void should_createAuthProviderConfig_when_usingAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        AuthProviderConfig config = new AuthProviderConfig(
                id, tenantId, "LDAP", "LDAP Auth", false, 2, "{\"url\":\"ldap://\"}", now, now);

        assertEquals(id, config.getId());
        assertEquals(tenantId, config.getTenantId());
        assertEquals("LDAP", config.getProviderType());
        assertEquals("LDAP Auth", config.getName());
        assertFalse(config.isEnabled());
        assertEquals(2, config.getPriority());
    }
}
