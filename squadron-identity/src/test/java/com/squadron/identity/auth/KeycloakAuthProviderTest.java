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
class KeycloakAuthProviderTest {

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    private KeycloakAuthProvider provider;

    @BeforeEach
    void setUp() {
        provider = new KeycloakAuthProvider(WebClient.builder(), tokenEncryptionService);
    }

    @Test
    void should_returnKeycloakProviderType_when_getProviderTypeCalled() {
        assertEquals(SecurityConstants.AUTH_PROVIDER_KEYCLOAK, provider.getProviderType());
    }

    @Test
    void should_supportKeycloak_when_supportsCalledWithKeycloak() {
        assertTrue(provider.supports(SecurityConstants.AUTH_PROVIDER_KEYCLOAK));
    }

    @Test
    void should_notSupportOther_when_supportsCalledWithOther() {
        assertFalse(provider.supports("ldap"));
        assertFalse(provider.supports("oidc"));
    }

    @Test
    void should_throwAuthException_when_configHasNoServerUrl() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .providerType("keycloak")
                .name("Keycloak")
                .config("{\"realm\":\"squadron\",\"clientId\":\"app\",\"clientSecret\":\"secret\"}")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // This should throw because the serverUrl is empty, leading to WebClient failure
        assertThrows(AuthenticationException.class, () ->
                provider.authenticate("user", "pass", config));
    }

    @Test
    void should_throwAuthException_when_configIsEmpty() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .providerType("keycloak")
                .name("Keycloak")
                .config("{}")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertThrows(AuthenticationException.class, () ->
                provider.authenticate("user", "pass", config));
    }
}
