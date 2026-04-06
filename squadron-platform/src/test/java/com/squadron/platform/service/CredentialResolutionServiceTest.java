package com.squadron.platform.service;

import com.squadron.common.audit.AuditEvent;
import com.squadron.common.audit.AuditService;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.CredentialPurpose;
import com.squadron.common.security.CredentialType;
import com.squadron.common.security.GitAuthMode;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.entity.SshKey;
import com.squadron.platform.entity.UserPlatformToken;
import com.squadron.platform.repository.PlatformConnectionRepository;
import com.squadron.platform.repository.SshKeyRepository;
import com.squadron.platform.repository.UserPlatformTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialResolutionServiceTest {

    @Mock
    private UserTokenService userTokenService;

    @Mock
    private UserPlatformTokenRepository tokenRepository;

    @Mock
    private SshKeyRepository sshKeyRepository;

    @Mock
    private SshKeyService sshKeyService;

    @Mock
    private PlatformConnectionRepository connectionRepository;

    @Mock
    private GitHubAppTokenService gitHubAppTokenService;

    @Mock
    private AuditService auditService;

    private CredentialResolutionService service;

    private UUID userId;
    private UUID connectionId;
    private PlatformConnection githubConnection;
    private PlatformConnection jiraConnection;

    @BeforeEach
    void setUp() {
        service = new CredentialResolutionService(
                userTokenService, tokenRepository, sshKeyRepository,
                sshKeyService, connectionRepository, gitHubAppTokenService,
                auditService);

        userId = UUID.randomUUID();
        connectionId = UUID.randomUUID();

        githubConnection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("GitHub Prod")
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("oauth2")
                .status("ACTIVE")
                .build();

        jiraConnection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("Jira Prod")
                .platformType("JIRA")
                .baseUrl("https://myorg.atlassian.net")
                .authType("oauth2")
                .status("ACTIVE")
                .build();
    }

    // --- Strategy 1: OAuth2 Token ---

    @Test
    void should_resolveOAuth2Token_when_oauth2TokenExists() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(githubConnection));

        UserPlatformToken oauth2Token = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-oauth2")
                .tokenType("oauth2")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.of(oauth2Token));
        when(userTokenService.getDecryptedAccessToken(userId, connectionId))
                .thenReturn("decrypted-oauth2-token");

        CredentialResolutionResult result = service.resolveCredentials(userId, connectionId, CredentialPurpose.PLATFORM_API);

        assertNotNull(result);
        assertEquals("decrypted-oauth2-token", result.getAccessToken());
        assertEquals(CredentialType.OAUTH2, result.getCredentialType());
        assertEquals(GitAuthMode.HTTPS_TOKEN, result.getGitAuthMode());
        assertNotNull(result.getExpiresAt());
    }

    // --- Strategy 2: PAT ---

    @Test
    void should_resolvePatToken_when_patTokenExists() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(githubConnection));

        UserPlatformToken patToken = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-pat")
                .tokenType("pat")
                .build();

        // First call (OAuth2 check) returns PAT (not oauth2), so tryOAuth2 returns null
        // Second call (PAT check) finds the PAT
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.of(patToken));
        when(userTokenService.getDecryptedAccessToken(userId, connectionId))
                .thenReturn("decrypted-pat-token");

        CredentialResolutionResult result = service.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_PUSH);

        assertNotNull(result);
        assertEquals("decrypted-pat-token", result.getAccessToken());
        assertEquals(CredentialType.PAT, result.getCredentialType());
        assertEquals(GitAuthMode.HTTPS_TOKEN, result.getGitAuthMode());
        assertNull(result.getExpiresAt());
    }

    // --- Strategy 3: Deploy Key ---

    @Test
    void should_resolveDeployKey_when_gitPurposeAndDeployKeyExists() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(jiraConnection));
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());

        UUID keyId = UUID.randomUUID();
        SshKey deployKey = SshKey.builder()
                .id(keyId)
                .tenantId(UUID.randomUUID())
                .connectionId(connectionId)
                .name("Deploy Key")
                .publicKey("ssh-ed25519 AAAA...")
                .privateKey("encrypted-private")
                .fingerprint("SHA256:abc")
                .keyUsage("DEPLOY_KEY")
                .build();

        when(sshKeyRepository.findByConnectionIdAndKeyUsage(connectionId, "DEPLOY_KEY"))
                .thenReturn(List.of(deployKey));
        when(sshKeyService.getDecryptedPrivateKey(keyId))
                .thenReturn("decrypted-ssh-private-key");
        when(userTokenService.getDecryptedAccessToken(userId, connectionId))
                .thenThrow(new ResourceNotFoundException("UserPlatformToken", "not found"));

        CredentialResolutionResult result = service.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE);

        assertNotNull(result);
        assertNull(result.getAccessToken()); // No API token available
        assertEquals("decrypted-ssh-private-key", result.getSshPrivateKey());
        assertEquals(CredentialType.DEPLOY_KEY, result.getCredentialType());
        assertEquals(GitAuthMode.SSH_KEY, result.getGitAuthMode());
    }

    @Test
    void should_resolveDeployKeyWithApiToken_when_bothExist() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(jiraConnection));
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());

        UUID keyId = UUID.randomUUID();
        SshKey deployKey = SshKey.builder()
                .id(keyId)
                .tenantId(UUID.randomUUID())
                .connectionId(connectionId)
                .name("Deploy Key")
                .publicKey("ssh-ed25519 AAAA...")
                .privateKey("encrypted-private")
                .fingerprint("SHA256:abc")
                .keyUsage("DEPLOY_KEY")
                .build();

        when(sshKeyRepository.findByConnectionIdAndKeyUsage(connectionId, "DEPLOY_KEY"))
                .thenReturn(List.of(deployKey));
        when(sshKeyService.getDecryptedPrivateKey(keyId))
                .thenReturn("decrypted-ssh-private-key");
        when(userTokenService.getDecryptedAccessToken(userId, connectionId))
                .thenReturn("api-token");

        CredentialResolutionResult result = service.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_PUSH);

        assertNotNull(result);
        assertEquals("api-token", result.getAccessToken());
        assertEquals("decrypted-ssh-private-key", result.getSshPrivateKey());
        assertEquals(CredentialType.DEPLOY_KEY, result.getCredentialType());
        assertEquals(GitAuthMode.SSH_KEY, result.getGitAuthMode());
    }

    @Test
    void should_skipDeployKey_when_purposeIsPlatformApi() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(jiraConnection));
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());

        // Deploy keys exist but purpose is PLATFORM_API, so they should be skipped
        // Since no credentials found, should throw
        assertThrows(ResourceNotFoundException.class,
                () -> service.resolveCredentials(userId, connectionId, CredentialPurpose.PLATFORM_API));

        verify(sshKeyRepository, never()).findByConnectionIdAndKeyUsage(any(), any());
    }

    // --- Strategy 4: GitHub App ---

    @Test
    void should_resolveGitHubAppToken_when_connectionIsGitHubApp() {
        PlatformConnection githubAppConnection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("GitHub App")
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("GITHUB_APP")
                .status("ACTIVE")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(githubAppConnection));
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());
        when(sshKeyRepository.findByConnectionIdAndKeyUsage(connectionId, "DEPLOY_KEY"))
                .thenReturn(List.of());
        when(gitHubAppTokenService.isGitHubApp(githubAppConnection)).thenReturn(true);
        when(gitHubAppTokenService.getInstallationToken(connectionId))
                .thenReturn("ghs_installationToken123");

        CredentialResolutionResult result = service.resolveCredentials(userId, connectionId, CredentialPurpose.FULL);

        assertNotNull(result);
        assertEquals("ghs_installationToken123", result.getAccessToken());
        assertEquals(CredentialType.GITHUB_APP, result.getCredentialType());
        assertEquals(GitAuthMode.HTTPS_TOKEN, result.getGitAuthMode());
        assertNotNull(result.getExpiresAt());
    }

    @Test
    void should_skipGitHubApp_when_notGitHubPlatform() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(jiraConnection));
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.resolveCredentials(userId, connectionId, CredentialPurpose.PLATFORM_API));

        verify(gitHubAppTokenService, never()).isGitHubApp(any());
    }

    // --- Fallback chain ---

    @Test
    void should_fallThroughToGitHubApp_when_oauth2AndPatFail() {
        PlatformConnection githubAppConnection = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("GitHub App")
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("GITHUB_APP")
                .status("ACTIVE")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(githubAppConnection));
        // OAuth2/PAT: no tokens
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());
        // Deploy key: none
        when(sshKeyRepository.findByConnectionIdAndKeyUsage(connectionId, "DEPLOY_KEY"))
                .thenReturn(List.of());
        // GitHub App: success
        when(gitHubAppTokenService.isGitHubApp(githubAppConnection)).thenReturn(true);
        when(gitHubAppTokenService.getInstallationToken(connectionId))
                .thenReturn("ghs_token");

        CredentialResolutionResult result = service.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE);

        assertEquals(CredentialType.GITHUB_APP, result.getCredentialType());
        assertEquals("ghs_token", result.getAccessToken());
    }

    @Test
    void should_preferOAuth2_overPat() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(githubConnection));

        UserPlatformToken oauth2Token = UserPlatformToken.builder()
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-oauth2")
                .tokenType("oauth2")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.of(oauth2Token));
        when(userTokenService.getDecryptedAccessToken(userId, connectionId))
                .thenReturn("oauth2-token");

        CredentialResolutionResult result = service.resolveCredentials(userId, connectionId, CredentialPurpose.FULL);

        assertEquals(CredentialType.OAUTH2, result.getCredentialType());
    }

    // --- Error cases ---

    @Test
    void should_throwNotFound_when_connectionDoesNotExist() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.resolveCredentials(userId, connectionId, CredentialPurpose.PLATFORM_API));
    }

    @Test
    void should_throwNotFound_when_noCredentialsAvailable() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(jiraConnection));
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.resolveCredentials(userId, connectionId, CredentialPurpose.PLATFORM_API));

        assertTrue(ex.getMessage().contains("No credentials found"));
    }

    @Test
    void should_continueChain_when_oauth2ThrowsException() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(jiraConnection));

        // OAuth2 lookup throws exception
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenThrow(new RuntimeException("DB error"));

        // Since all strategies fail, should throw ResourceNotFoundException
        assertThrows(ResourceNotFoundException.class,
                () -> service.resolveCredentials(userId, connectionId, CredentialPurpose.PLATFORM_API));
    }

    // --- isGitPurpose ---

    @Test
    void should_returnTrue_when_purposeIsGitClone() {
        assertTrue(service.isGitPurpose(CredentialPurpose.GIT_CLONE));
    }

    @Test
    void should_returnTrue_when_purposeIsGitPush() {
        assertTrue(service.isGitPurpose(CredentialPurpose.GIT_PUSH));
    }

    @Test
    void should_returnTrue_when_purposeIsFull() {
        assertTrue(service.isGitPurpose(CredentialPurpose.FULL));
    }

    @Test
    void should_returnFalse_when_purposeIsPlatformApi() {
        assertFalse(service.isGitPurpose(CredentialPurpose.PLATFORM_API));
    }

    // --- Audit logging ---

    @Test
    void should_auditSuccessfulCredentialResolution() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(githubConnection));

        UserPlatformToken oauth2Token = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-oauth2")
                .tokenType("oauth2")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.of(oauth2Token));
        when(userTokenService.getDecryptedAccessToken(userId, connectionId))
                .thenReturn("decrypted-oauth2-token");

        service.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_PUSH);

        verify(auditService).logEvent(argThat((AuditEvent event) ->
                "CREDENTIAL_RESOLVED".equals(event.getAction())
                && "platform-service".equals(event.getSourceService())
                && userId.equals(event.getUserId())
                && connectionId.toString().equals(event.getResourceId())
                && event.getDetails().contains("OAUTH2")
                && event.getDetails().contains("GIT_PUSH")
        ));
    }

    @Test
    void should_auditFailedCredentialResolution() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(jiraConnection));
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.resolveCredentials(userId, connectionId, CredentialPurpose.PLATFORM_API));

        verify(auditService).logEvent(argThat((AuditEvent event) ->
                "CREDENTIAL_RESOLUTION_FAILED".equals(event.getAction())
                && "platform-service".equals(event.getSourceService())
                && userId.equals(event.getUserId())
                && connectionId.toString().equals(event.getResourceId())
                && event.getDetails().contains("No credential strategy succeeded")
        ));
    }
}
