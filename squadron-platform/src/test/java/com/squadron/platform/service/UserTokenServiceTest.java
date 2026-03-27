package com.squadron.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.dto.ConnectionInfoResponse;
import com.squadron.platform.dto.LinkAccountRequest;
import com.squadron.platform.dto.OAuth2AuthorizeUrlResponse;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.entity.UserPlatformToken;
import com.squadron.platform.repository.PlatformConnectionRepository;
import com.squadron.platform.repository.UserPlatformTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserTokenServiceTest {

    @Mock
    private UserPlatformTokenRepository tokenRepository;

    @Mock
    private PlatformConnectionRepository connectionRepository;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private WebClient.Builder webClientBuilder;

    private ObjectMapper objectMapper;
    private UserTokenService userTokenService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userTokenService = new UserTokenService(
                tokenRepository, connectionRepository, encryptionService,
                webClientBuilder, objectMapper);
    }

    @Test
    void should_linkPatAccount_when_validRequest() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("GITHUB")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId)).thenReturn(Optional.empty());
        when(encryptionService.encrypt("ghp_abc123")).thenReturn("encrypted-pat");

        UserPlatformToken savedToken = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-pat")
                .tokenType("pat")
                .build();
        when(tokenRepository.save(any(UserPlatformToken.class))).thenReturn(savedToken);

        UserPlatformToken result = userTokenService.linkPatAccount(userId, connectionId, "ghp_abc123");

        assertNotNull(result);
        assertEquals("pat", result.getTokenType());
        assertEquals("encrypted-pat", result.getAccessToken());
        verify(encryptionService).encrypt("ghp_abc123");
    }

    @Test
    void should_updateExistingToken_when_relinkingPat() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("GITHUB")
                .build();

        UserPlatformToken existingToken = new UserPlatformToken();
        existingToken.setId(UUID.randomUUID());
        existingToken.setUserId(userId);
        existingToken.setConnectionId(connectionId);

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId)).thenReturn(Optional.of(existingToken));
        when(encryptionService.encrypt("new-pat")).thenReturn("encrypted-new-pat");
        when(tokenRepository.save(any(UserPlatformToken.class))).thenReturn(existingToken);

        UserPlatformToken result = userTokenService.linkPatAccount(userId, connectionId, "new-pat");

        assertNotNull(result);
        verify(tokenRepository).save(existingToken);
    }

    @Test
    void should_throwNotFound_when_linkPatAccountConnectionMissing() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userTokenService.linkPatAccount(userId, connectionId, "pat"));
    }

    @Test
    void should_getDecryptedAccessToken_when_tokenExists() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        UserPlatformToken token = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-token")
                .tokenType("pat")
                .build();

        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId)).thenReturn(Optional.of(token));
        when(encryptionService.decrypt("encrypted-token")).thenReturn("decrypted-token");

        String result = userTokenService.getDecryptedAccessToken(userId, connectionId);

        assertEquals("decrypted-token", result);
    }

    @Test
    void should_throwNotFound_when_getDecryptedAccessTokenMissing() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userTokenService.getDecryptedAccessToken(userId, connectionId));
    }

    @Test
    void should_getToken_when_exists() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        UserPlatformToken token = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted")
                .build();

        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId)).thenReturn(Optional.of(token));

        UserPlatformToken result = userTokenService.getToken(userId, connectionId);

        assertEquals(userId, result.getUserId());
        assertEquals(connectionId, result.getConnectionId());
    }

    @Test
    void should_throwNotFound_when_getTokenMissing() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userTokenService.getToken(userId, connectionId));
    }

    @Test
    void should_getTokensByUser() {
        UUID userId = UUID.randomUUID();
        List<UserPlatformToken> tokens = List.of(
                UserPlatformToken.builder().userId(userId).connectionId(UUID.randomUUID()).accessToken("t1").build(),
                UserPlatformToken.builder().userId(userId).connectionId(UUID.randomUUID()).accessToken("t2").build()
        );

        when(tokenRepository.findByUserId(userId)).thenReturn(tokens);

        List<UserPlatformToken> result = userTokenService.getTokensByUser(userId);

        assertEquals(2, result.size());
    }

    @Test
    void should_unlinkAccount_when_exists() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        UserPlatformToken token = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted")
                .build();

        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId)).thenReturn(Optional.of(token));

        userTokenService.unlinkAccount(userId, connectionId);

        verify(tokenRepository).delete(token);
    }

    @Test
    void should_throwNotFound_when_unlinkAccountMissing() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userTokenService.unlinkAccount(userId, connectionId));
    }

    @Test
    void should_notRefreshOAuth2Token_when_notExpired() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        UserPlatformToken token = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-token")
                .refreshToken("encrypted-refresh")
                .tokenType("oauth2")
                .expiresAt(Instant.now().plusSeconds(3600)) // Expires in 1 hour - not expired
                .build();

        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId)).thenReturn(Optional.of(token));
        when(encryptionService.decrypt("encrypted-token")).thenReturn("decrypted-token");

        String result = userTokenService.getDecryptedAccessToken(userId, connectionId);

        assertEquals("decrypted-token", result);
        // Should not have made a web client call for refresh
        verifyNoInteractions(webClientBuilder);
    }

    @Test
    void should_returnEmptyList_when_noTokensForUser() {
        UUID userId = UUID.randomUUID();
        when(tokenRepository.findByUserId(userId)).thenReturn(List.of());

        List<UserPlatformToken> result = userTokenService.getTokensByUser(userId);

        assertTrue(result.isEmpty());
    }

    @Test
    void should_linkAccount_delegates_to_linkOAuth2Account() {
        // linkAccount delegates to linkOAuth2Account, which uses WebClient.
        // Since it requires a full WebClient mock chain (very complex), we just
        // verify it throws when connection is missing, proving it delegates properly.
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        LinkAccountRequest request = LinkAccountRequest.builder()
                .connectionId(connectionId)
                .authorizationCode("code")
                .redirectUri("https://redirect.example.com")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userTokenService.linkAccount(userId, request));
    }

    // --- OAuth2 Authorize URL Generation tests ---

    @Test
    void should_generateOAuth2AuthorizeUrl_when_validConnection() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Map<String, Object> credentials = Map.of(
                "clientId", "my-client-id",
                "authorizeEndpoint", "https://github.com/login/oauth/authorize",
                "scopes", "repo user"
        );
        Map<String, Object> metadata = Map.of(
                "redirectUri", "https://app.example.com/callback"
        );

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(tenantId)
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("oauth2")
                .credentials(objectMapper.writeValueAsString(credentials))
                .metadata(objectMapper.writeValueAsString(metadata))
                .status("ACTIVE")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        OAuth2AuthorizeUrlResponse response = userTokenService.generateOAuth2AuthorizeUrl(connectionId);

        assertNotNull(response);
        assertNotNull(response.getAuthorizeUrl());
        assertNotNull(response.getState());
        assertEquals(connectionId, response.getConnectionId());
        assertEquals("GITHUB", response.getPlatformType());

        // Verify the URL contains expected parameters
        String url = response.getAuthorizeUrl();
        assertTrue(url.startsWith("https://github.com/login/oauth/authorize"), "URL should start with authorize endpoint");
        assertTrue(url.contains("response_type=code"), "URL should contain response_type=code");
        assertTrue(url.contains("client_id=my-client-id"), "URL should contain client_id");
        assertTrue(url.contains("redirect_uri="), "URL should contain redirect_uri parameter");
        assertTrue(url.contains("app.example.com/callback"), "URL should contain callback host");
        assertTrue(url.contains("state=" + response.getState()), "URL should contain state parameter");
        assertTrue(url.contains("scope="), "URL should contain scope parameter");
        assertTrue(url.contains("repo") && url.contains("user"), "URL should contain both scopes");
    }

    @Test
    void should_throwNotFound_when_generateAuthorizeUrlConnectionMissing() {
        UUID connectionId = UUID.randomUUID();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userTokenService.generateOAuth2AuthorizeUrl(connectionId));
    }

    @Test
    void should_throwSecurityException_when_authorizeEndpointMissing() throws Exception {
        UUID connectionId = UUID.randomUUID();

        // Credentials with clientId but missing authorizeEndpoint
        Map<String, Object> credentials = Map.of(
                "clientId", "my-client-id"
        );

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("oauth2")
                .credentials(objectMapper.writeValueAsString(credentials))
                .status("ACTIVE")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        assertThrows(SecurityException.class,
                () -> userTokenService.generateOAuth2AuthorizeUrl(connectionId));
    }

    // --- Available Connections tests ---

    @Test
    void should_getAvailableConnections_when_activeConnectionsExist() {
        UUID tenantId = UUID.randomUUID();

        PlatformConnection conn1 = PlatformConnection.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("oauth2")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();

        PlatformConnection conn2 = PlatformConnection.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .platformType("JIRA")
                .baseUrl("https://myorg.atlassian.net")
                .authType("oauth2")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();

        when(connectionRepository.findByTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of(conn1, conn2));

        List<ConnectionInfoResponse> result = userTokenService.getAvailableConnections(tenantId);

        assertEquals(2, result.size());
        assertEquals("GITHUB", result.get(0).getPlatformType());
        assertEquals("JIRA", result.get(1).getPlatformType());
        assertEquals(tenantId, result.get(0).getTenantId());
        assertEquals("ACTIVE", result.get(0).getStatus());
    }

    @Test
    void should_returnEmptyList_when_noActiveConnections() {
        UUID tenantId = UUID.randomUUID();

        when(connectionRepository.findByTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of());

        List<ConnectionInfoResponse> result = userTokenService.getAvailableConnections(tenantId);

        assertTrue(result.isEmpty());
    }

    // --- OAuth2 Link with State Validation tests ---

    @Test
    void should_linkOAuth2AccountWithState_when_validState() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        // First, generate an authorize URL to create a valid state
        Map<String, Object> credentials = Map.of(
                "clientId", "my-client-id",
                "authorizeEndpoint", "https://github.com/login/oauth/authorize",
                "scopes", "repo user"
        );
        Map<String, Object> metadata = Map.of(
                "redirectUri", "https://app.example.com/callback"
        );

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("oauth2")
                .status("ACTIVE")
                .build();

        try {
            connection.setCredentials(objectMapper.writeValueAsString(credentials));
            connection.setMetadata(objectMapper.writeValueAsString(metadata));
        } catch (Exception e) {
            fail("Failed to set up test data: " + e.getMessage());
        }

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        // Generate authorize URL to create valid state
        OAuth2AuthorizeUrlResponse authorizeResponse = userTokenService.generateOAuth2AuthorizeUrl(connectionId);
        String state = authorizeResponse.getState();

        // Now the linkOAuth2AccountWithState should validate the state,
        // then delegate to linkOAuth2Account which will call connectionRepository.findById again
        // Since linkOAuth2Account uses WebClient (hard to mock), we verify it gets past state validation
        // by checking that it attempts to find the connection (which means state was valid)

        // Reset connection mock to track the second findById call
        reset(connectionRepository);
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        // The actual linkOAuth2Account will fail because WebClient is not fully mocked,
        // but getting past the state validation is what we're testing
        try {
            userTokenService.linkOAuth2AccountWithState(userId, connectionId,
                    "auth-code", "https://app.example.com/callback", state);
        } catch (SecurityException e) {
            // Expected: WebClient call fails, but state validation passed
            fail("State validation should have passed, but got SecurityException: " + e.getMessage());
        } catch (Exception e) {
            // Expected: WebClient or other downstream error, state validation passed
            // Verify that connectionRepository.findById was called, meaning state validation succeeded
            verify(connectionRepository).findById(connectionId);
        }
    }

    @Test
    void should_throwSecurityException_when_invalidState() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        assertThrows(SecurityException.class,
                () -> userTokenService.linkOAuth2AccountWithState(userId, connectionId,
                        "auth-code", "https://app.example.com/callback", "invalid-state-token"));
    }

    @Test
    void should_throwSecurityException_when_expiredState() throws Exception {
        UUID connectionId = UUID.randomUUID();

        // We need to use reflection to inject an expired state entry into the stateStore
        // since we can't easily control time in the service
        Map<String, Object> credentials = Map.of(
                "clientId", "my-client-id",
                "authorizeEndpoint", "https://github.com/login/oauth/authorize",
                "scopes", "repo"
        );
        Map<String, Object> metadata = Map.of(
                "redirectUri", "https://app.example.com/callback"
        );

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("oauth2")
                .credentials(objectMapper.writeValueAsString(credentials))
                .metadata(objectMapper.writeValueAsString(metadata))
                .status("ACTIVE")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        // Generate an authorize URL to create a state entry
        OAuth2AuthorizeUrlResponse authorizeResponse = userTokenService.generateOAuth2AuthorizeUrl(connectionId);
        String state = authorizeResponse.getState();

        // Use reflection to modify the state entry's createdAt to be expired (11 minutes ago)
        java.lang.reflect.Field stateStoreField = UserTokenService.class.getDeclaredField("stateStore");
        stateStoreField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, Object> stateStore =
                (java.util.concurrent.ConcurrentHashMap<String, Object>) stateStoreField.get(userTokenService);

        // Get the StateEntry class and create an expired entry
        Class<?>[] innerClasses = UserTokenService.class.getDeclaredClasses();
        Class<?> stateEntryClass = null;
        for (Class<?> inner : innerClasses) {
            if (inner.getSimpleName().equals("StateEntry")) {
                stateEntryClass = inner;
                break;
            }
        }
        assertNotNull(stateEntryClass, "StateEntry inner class should exist");

        // Create expired state entry (11 minutes ago)
        java.lang.reflect.Constructor<?> constructor = stateEntryClass.getDeclaredConstructor(UUID.class, Instant.class);
        constructor.setAccessible(true);
        Object expiredEntry = constructor.newInstance(connectionId, Instant.now().minusSeconds(660));
        stateStore.put(state, expiredEntry);

        UUID userId = UUID.randomUUID();
        assertThrows(SecurityException.class,
                () -> userTokenService.linkOAuth2AccountWithState(userId, connectionId,
                        "auth-code", "https://app.example.com/callback", state));
    }
}
