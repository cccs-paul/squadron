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

    // --- ldapEscape tests ---

    @Test
    void should_escapeBackslash() {
        assertEquals("user\\5cname", LdapAuthProvider.ldapEscape("user\\name"));
    }

    @Test
    void should_escapeAsterisk() {
        assertEquals("user\\2a", LdapAuthProvider.ldapEscape("user*"));
    }

    @Test
    void should_escapeParentheses() {
        assertEquals("\\28user\\29", LdapAuthProvider.ldapEscape("(user)"));
    }

    @Test
    void should_escapeNullByte() {
        assertEquals("user\\00name", LdapAuthProvider.ldapEscape("user\0name"));
    }

    @Test
    void should_escapeSlash() {
        assertEquals("user\\2fname", LdapAuthProvider.ldapEscape("user/name"));
    }

    @Test
    void should_returnNullForNullInput() {
        assertNull(LdapAuthProvider.ldapEscape(null));
    }

    @Test
    void should_notEscapeNormalChars() {
        assertEquals("normaluser", LdapAuthProvider.ldapEscape("normaluser"));
    }

    @Test
    void should_escapeComplexInjectionAttempt() {
        String malicious = "user)(|(uid=*))";
        String escaped = LdapAuthProvider.ldapEscape(malicious);
        assertEquals("user\\29\\28|\\28uid=\\2a\\29\\29", escaped);
    }
}
