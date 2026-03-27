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
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OidcAuthProviderTest {

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    private OidcAuthProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OidcAuthProvider(WebClient.builder(), tokenEncryptionService);
    }

    @Test
    void should_returnOidcProviderType_when_getProviderTypeCalled() {
        assertEquals(SecurityConstants.AUTH_PROVIDER_OIDC, provider.getProviderType());
    }

    @Test
    void should_supportOidc_when_supportsCalledWithOidc() {
        assertTrue(provider.supports(SecurityConstants.AUTH_PROVIDER_OIDC));
    }

    @Test
    void should_notSupportOther_when_supportsCalledWithOther() {
        assertFalse(provider.supports("ldap"));
        assertFalse(provider.supports("keycloak"));
    }

    @Test
    void should_throwUnsupportedOperation_when_authenticateCalledWithPassword() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .providerType("oidc")
                .name("OIDC")
                .config("{}")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                provider.authenticate("user", "pass", config));
    }

    @Test
    void should_throwAuthException_when_authenticateWithCodeHasEmptyConfig() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .providerType("oidc")
                .name("OIDC")
                .config("{}")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Should throw because tokenEndpoint is empty
        assertThrows(AuthenticationException.class, () ->
                provider.authenticateWithCode("code123", "http://localhost/callback", config));
    }
}
