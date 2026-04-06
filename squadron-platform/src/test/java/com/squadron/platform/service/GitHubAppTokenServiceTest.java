package com.squadron.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.repository.PlatformConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubAppTokenServiceTest {

    @Mock
    private PlatformConnectionRepository connectionRepository;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    private ObjectMapper objectMapper;
    private GitHubAppTokenService service;

    private UUID connectionId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new GitHubAppTokenService(
                connectionRepository, encryptionService, webClientBuilder, objectMapper);
        connectionId = UUID.randomUUID();
    }

    // --- isGitHubApp ---

    @Test
    void should_returnTrue_when_connectionIsGitHubApp() {
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("GitHub App")
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("GITHUB_APP")
                .status("ACTIVE")
                .build();

        assertTrue(service.isGitHubApp(connection));
    }

    @Test
    void should_returnFalse_when_authTypeIsNotGitHubApp() {
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("GitHub OAuth")
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("oauth2")
                .status("ACTIVE")
                .build();

        assertFalse(service.isGitHubApp(connection));
    }

    @Test
    void should_returnFalse_when_platformIsNotGitHub() {
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("GitLab App")
                .platformType("GITLAB")
                .baseUrl("https://gitlab.com")
                .authType("GITHUB_APP")
                .status("ACTIVE")
                .build();

        assertFalse(service.isGitHubApp(connection));
    }

    @Test
    void should_returnTrue_when_caseInsensitiveMatch() {
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("GitHub App")
                .platformType("github")
                .baseUrl("https://api.github.com")
                .authType("github_app")
                .status("ACTIVE")
                .build();

        assertTrue(service.isGitHubApp(connection));
    }

    // --- getInstallationToken ---

    @Test
    void should_throwIllegalState_when_connectionNotFound() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.getInstallationToken(connectionId));
    }

    @Test
    void should_throwIllegalState_when_connectionNotGitHubApp() {
        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("GitHub OAuth")
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("oauth2")
                .credentials("{\"clientId\":\"abc\"}")
                .status("ACTIVE")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.getInstallationToken(connectionId));
        assertTrue(ex.getMessage().contains("not a GitHub App"));
    }

    // --- generateAppJwt ---

    @Test
    void should_generateValidJwt_when_validCredentials() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        String pkcs8Pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(privateKey.getEncoded())
                + "\n-----END PRIVATE KEY-----";

        when(encryptionService.isEncrypted(pkcs8Pem)).thenReturn(false);

        Map<String, Object> creds = Map.of(
                "appId", "12345",
                "installationId", "67890",
                "privateKeyPem", pkcs8Pem
        );

        String jwt = service.generateAppJwt(creds);

        assertNotNull(jwt);
        // JWT has 3 dot-separated parts
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
    }

    @Test
    void should_decryptPem_when_encrypted() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        String pkcs8Pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(privateKey.getEncoded())
                + "\n-----END PRIVATE KEY-----";

        String encryptedPem = "ENCRYPTED_PEM_DATA";

        when(encryptionService.isEncrypted(encryptedPem)).thenReturn(true);
        when(encryptionService.decrypt(encryptedPem)).thenReturn(pkcs8Pem);

        Map<String, Object> creds = Map.of(
                "appId", "12345",
                "installationId", "67890",
                "privateKeyPem", encryptedPem
        );

        String jwt = service.generateAppJwt(creds);

        assertNotNull(jwt);
        verify(encryptionService).decrypt(encryptedPem);
    }

    @Test
    void should_throwIllegalState_when_invalidPrivateKey() {
        when(encryptionService.isEncrypted("not-a-key")).thenReturn(false);

        Map<String, Object> creds = Map.of(
                "appId", "12345",
                "installationId", "67890",
                "privateKeyPem", "not-a-key"
        );

        assertThrows(IllegalStateException.class, () -> service.generateAppJwt(creds));
    }

    // --- parseRSAPrivateKey ---

    @Test
    void should_parseRSAPrivateKey_when_validPKCS8Pem() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey expectedKey = (RSAPrivateKey) keyPair.getPrivate();

        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(expectedKey.getEncoded())
                + "\n-----END PRIVATE KEY-----";

        RSAPrivateKey parsedKey = service.parseRSAPrivateKey(pem);

        assertNotNull(parsedKey);
        assertEquals(expectedKey, parsedKey);
    }

    @Test
    void should_throwException_when_invalidPemData() {
        assertThrows(Exception.class, () -> service.parseRSAPrivateKey("not-a-valid-pem"));
    }

    // --- Cache behavior ---

    @Test
    void should_returnCachedToken_when_notExpired() throws Exception {
        // Use reflection or a controlled test to verify caching behavior
        // We test this by calling getInstallationToken twice and verifying
        // the WebClient is only called once

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        String pkcs8Pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(privateKey.getEncoded())
                + "\n-----END PRIVATE KEY-----";

        String credsJson = objectMapper.writeValueAsString(Map.of(
                "appId", "12345",
                "installationId", "67890",
                "privateKeyPem", pkcs8Pem
        ));

        PlatformConnection connection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("GitHub App")
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("GITHUB_APP")
                .credentials(credsJson)
                .status("ACTIVE")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(encryptionService.isEncrypted(pkcs8Pem)).thenReturn(false);

        // Mock WebClient chain for the exchange
        var webClient = mock(org.springframework.web.reactive.function.client.WebClient.class);
        var requestBodyUriSpec = mock(org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec.class);
        var requestBodySpec = mock(org.springframework.web.reactive.function.client.WebClient.RequestBodySpec.class);
        var requestHeadersSpec = mock(org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec.class);
        var responseSpec = mock(org.springframework.web.reactive.function.client.WebClient.ResponseSpec.class);
        var mono = mock(reactor.core.publisher.Mono.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq("Authorization"), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq("Accept"), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class))).thenReturn(mono);
        when(mono.block()).thenReturn(Map.of("token", "ghs_firstToken123"));

        // First call: should hit WebClient
        String token1 = service.getInstallationToken(connectionId);
        assertEquals("ghs_firstToken123", token1);

        // Second call: should return cached token (WebClient build called only once)
        String token2 = service.getInstallationToken(connectionId);
        assertEquals("ghs_firstToken123", token2);

        // WebClient.build() should have been called only once
        verify(webClientBuilder, times(1)).build();
    }

    // --- clearCache ---

    @Test
    void should_clearTokenCache_when_clearCacheCalled() {
        // Simply verify the method doesn't throw
        service.clearCache();
        // No assertion needed - method should not throw
    }
}
