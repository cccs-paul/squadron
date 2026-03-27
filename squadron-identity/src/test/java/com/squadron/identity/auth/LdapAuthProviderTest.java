package com.squadron.identity.auth;

import com.squadron.common.security.SecurityConstants;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.exception.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LdapAuthProviderTest {

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    private LdapAuthProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LdapAuthProvider(tokenEncryptionService);
    }

    @Test
    void should_returnLdapProviderType_when_getProviderTypeCalled() {
        assertEquals(SecurityConstants.AUTH_PROVIDER_LDAP, provider.getProviderType());
    }

    @Test
    void should_supportLdap_when_supportsCalledWithLdap() {
        assertTrue(provider.supports(SecurityConstants.AUTH_PROVIDER_LDAP));
    }

    @Test
    void should_notSupportOther_when_supportsCalledWithOther() {
        assertFalse(provider.supports("oidc"));
        assertFalse(provider.supports("keycloak"));
    }

    @Test
    void should_throwAuthException_when_configHasNoUrl() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .providerType("ldap")
                .name("LDAP")
                .config("{\"baseDn\":\"dc=example,dc=com\"}")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertThrows(AuthenticationException.class, () ->
                provider.authenticate("user", "pass", config));
    }

    @Test
    void should_throwAuthException_when_configIsNull() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .providerType("ldap")
                .name("LDAP")
                .config(null)
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertThrows(AuthenticationException.class, () ->
                provider.authenticate("user", "pass", config));
    }

    @Test
    void should_throwAuthException_when_ldapServerUnreachable() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .providerType("ldap")
                .name("LDAP")
                .config("{\"url\":\"ldap://nonexistent.invalid:389\",\"baseDn\":\"dc=test\",\"directoryType\":\"openldap\"}")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertThrows(AuthenticationException.class, () ->
                provider.authenticate("user", "pass", config));
    }
}
