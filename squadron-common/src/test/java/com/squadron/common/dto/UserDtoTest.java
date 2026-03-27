package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Set<String> roles = Set.of("ADMIN", "DEVELOPER");
        Map<String, Object> settings = Map.of("theme", "dark", "notifications", true);
        Instant now = Instant.now();

        UserDto dto = UserDto.builder()
                .id(id)
                .tenantId(tenantId)
                .externalId("ldap-dn-123")
                .email("john@example.com")
                .displayName("John Doe")
                .role("ADMIN")
                .authProvider("ldap")
                .roles(roles)
                .settings(settings)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("ldap-dn-123", dto.getExternalId());
        assertEquals("john@example.com", dto.getEmail());
        assertEquals("John Doe", dto.getDisplayName());
        assertEquals("ADMIN", dto.getRole());
        assertEquals("ldap", dto.getAuthProvider());
        assertEquals(roles, dto.getRoles());
        assertEquals(settings, dto.getSettings());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        UserDto dto = new UserDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getExternalId());
        assertNull(dto.getEmail());
        assertNull(dto.getDisplayName());
        assertNull(dto.getRole());
        assertNull(dto.getAuthProvider());
        assertNull(dto.getRoles());
        assertNull(dto.getSettings());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Set<String> roles = Set.of("VIEWER");
        Map<String, Object> settings = Map.of("lang", "en");
        Instant created = Instant.now();
        Instant updated = Instant.now();

        UserDto dto = new UserDto(
                id, tenantId, "oidc-sub-456", "jane@example.com",
                "Jane Smith", "VIEWER", "oidc", roles, settings, created, updated
        );

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("oidc-sub-456", dto.getExternalId());
        assertEquals("jane@example.com", dto.getEmail());
        assertEquals("Jane Smith", dto.getDisplayName());
        assertEquals("VIEWER", dto.getRole());
        assertEquals("oidc", dto.getAuthProvider());
        assertEquals(roles, dto.getRoles());
        assertEquals(settings, dto.getSettings());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(updated, dto.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        UserDto dto = new UserDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setEmail("updated@example.com");
        dto.setDisplayName("Updated User");
        dto.setRole("DEVELOPER");
        dto.setAuthProvider("keycloak");
        dto.setRoles(Set.of("DEVELOPER", "REVIEWER"));

        assertEquals(id, dto.getId());
        assertEquals("updated@example.com", dto.getEmail());
        assertEquals("Updated User", dto.getDisplayName());
        assertEquals("DEVELOPER", dto.getRole());
        assertEquals("keycloak", dto.getAuthProvider());
        assertEquals(Set.of("DEVELOPER", "REVIEWER"), dto.getRoles());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        UserDto dto1 = UserDto.builder()
                .id(id)
                .tenantId(tenantId)
                .email("test@test.com")
                .role("ADMIN")
                .build();

        UserDto dto2 = UserDto.builder()
                .id(id)
                .tenantId(tenantId)
                .email("test@test.com")
                .role("ADMIN")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        UserDto dto1 = UserDto.builder()
                .email("user1@test.com")
                .build();

        UserDto dto2 = UserDto.builder()
                .email("user2@test.com")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        UserDto dto = UserDto.builder()
                .email("john@example.com")
                .displayName("John Doe")
                .authProvider("ldap")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("john@example.com"));
        assertTrue(str.contains("John Doe"));
        assertTrue(str.contains("ldap"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        UserDto dto = UserDto.builder()
                .id(null)
                .tenantId(null)
                .externalId(null)
                .email(null)
                .displayName(null)
                .role(null)
                .authProvider(null)
                .roles(null)
                .settings(null)
                .createdAt(null)
                .updatedAt(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getExternalId());
        assertNull(dto.getEmail());
        assertNull(dto.getDisplayName());
        assertNull(dto.getRole());
        assertNull(dto.getAuthProvider());
        assertNull(dto.getRoles());
        assertNull(dto.getSettings());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_handleEmptyCollections_when_emptyCollectionsProvided() {
        UserDto dto = UserDto.builder()
                .roles(Set.of())
                .settings(Map.of())
                .build();

        assertNotNull(dto.getRoles());
        assertTrue(dto.getRoles().isEmpty());
        assertNotNull(dto.getSettings());
        assertTrue(dto.getSettings().isEmpty());
    }

    @Test
    void should_supportMultipleAuthProviders_when_differentProvidersSet() {
        UserDto ldapUser = UserDto.builder()
                .authProvider("ldap")
                .externalId("cn=john,ou=users,dc=example,dc=com")
                .build();
        assertEquals("ldap", ldapUser.getAuthProvider());

        UserDto oidcUser = UserDto.builder()
                .authProvider("oidc")
                .externalId("google-oauth2|123456")
                .build();
        assertEquals("oidc", oidcUser.getAuthProvider());

        UserDto keycloakUser = UserDto.builder()
                .authProvider("keycloak")
                .externalId("kc-sub-789")
                .build();
        assertEquals("keycloak", keycloakUser.getAuthProvider());
    }
}
