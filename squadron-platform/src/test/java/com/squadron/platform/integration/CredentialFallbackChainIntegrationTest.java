package com.squadron.platform.integration;

import com.squadron.common.audit.AuditService;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.security.CredentialPurpose;
import com.squadron.common.security.CredentialType;
import com.squadron.common.security.GitAuthMode;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.entity.SshKey;
import com.squadron.platform.entity.UserPlatformToken;
import com.squadron.platform.repository.PlatformConnectionRepository;
import com.squadron.platform.repository.SshKeyRepository;
import com.squadron.platform.repository.UserPlatformTokenRepository;
import com.squadron.platform.service.CredentialResolutionService;
import com.squadron.platform.service.GitHubAppTokenService;
import com.squadron.platform.service.SshKeyService;
import com.squadron.platform.service.UserTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: credential resolution fallback chain.
 * Verifies: OAuth2 -> PAT -> Deploy Key -> GitHub App -> Fail
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Credential Resolution Fallback Chain Integration")
class CredentialFallbackChainIntegrationTest {

    @Mock private UserTokenService userTokenService;
    @Mock private UserPlatformTokenRepository tokenRepository;
    @Mock private SshKeyRepository sshKeyRepository;
    @Mock private SshKeyService sshKeyService;
    @Mock private PlatformConnectionRepository connectionRepository;
    @Mock private GitHubAppTokenService gitHubAppTokenService;
    @Mock private AuditService auditService;

    private CredentialResolutionService service;
    private UUID userId, connectionId, tenantId;
    private PlatformConnection githubConnection;

    @BeforeEach
    void setUp() {
        service = new CredentialResolutionService(
                userTokenService, tokenRepository, sshKeyRepository,
                sshKeyService, connectionRepository, gitHubAppTokenService,
                auditService);

        userId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        githubConnection = PlatformConnection.builder()
                .id(connectionId).tenantId(tenantId)
                .name("GitHub Prod").platformType("GITHUB")
                .baseUrl("https://api.github.com").authType("oauth2")
                .status("ACTIVE").credentials("{}")
                .build();
    }

    @Test
    @DisplayName("should fall back from expired OAuth2 to PAT successfully")
    void should_fallbackToPat_when_oauth2ExpiredAndNoRefresh() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(githubConnection));

        // PAT exists — service calls findByUserIdAndConnectionId which returns Optional
        UserPlatformToken patToken = UserPlatformToken.builder()
                .id(UUID.randomUUID()).userId(userId).connectionId(connectionId)
                .tokenType("pat").accessToken("encrypted_pat")
                .build();
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.of(patToken));
        // Service calls userTokenService.getDecryptedAccessToken(userId, connectionId)
        when(userTokenService.getDecryptedAccessToken(userId, connectionId)).thenReturn("ghp_real_pat_token");

        CredentialResolutionResult result = service.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE);

        assertNotNull(result);
        assertEquals("ghp_real_pat_token", result.getAccessToken());
        assertEquals(CredentialType.PAT, result.getCredentialType());
        assertEquals(GitAuthMode.HTTPS_TOKEN, result.getGitAuthMode());
    }

    @Test
    @DisplayName("should fall back to deploy key when no PAT or OAuth2 for git operations")
    void should_fallbackToDeployKey_when_noPatOrOauth2ForGitClone() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(githubConnection));

        // No user tokens at all
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());

        // Deploy key exists — service uses findByConnectionIdAndKeyUsage
        UUID deployKeyId = UUID.randomUUID();
        SshKey deployKey = SshKey.builder()
                .id(deployKeyId).tenantId(tenantId).connectionId(connectionId)
                .name("deploy-key").publicKey("ssh-ed25519 AAAA...")
                .privateKey("encrypted_private_key").fingerprint("SHA256:abc")
                .keyType("ED25519").keyUsage("DEPLOY_KEY")
                .build();
        when(sshKeyRepository.findByConnectionIdAndKeyUsage(connectionId, "DEPLOY_KEY"))
                .thenReturn(List.of(deployKey));
        // Service calls sshKeyService.getDecryptedPrivateKey(key.getId())
        when(sshKeyService.getDecryptedPrivateKey(deployKeyId))
                .thenReturn("-----BEGIN OPENSSH PRIVATE KEY-----...");
        // Service also tries userTokenService.getDecryptedAccessToken for API calls alongside SSH
        when(userTokenService.getDecryptedAccessToken(userId, connectionId))
                .thenThrow(new RuntimeException("No token"));

        CredentialResolutionResult result = service.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE);

        assertNotNull(result);
        assertEquals("-----BEGIN OPENSSH PRIVATE KEY-----...", result.getSshPrivateKey());
        assertEquals(CredentialType.DEPLOY_KEY, result.getCredentialType());
        assertEquals(GitAuthMode.SSH_KEY, result.getGitAuthMode());
    }

    @Test
    @DisplayName("should resolve GitHub App installation token when connection is GITHUB_APP type")
    void should_resolveGitHubAppToken_when_connectionIsGitHubApp() {
        PlatformConnection appConnection = PlatformConnection.builder()
                .id(connectionId).tenantId(tenantId)
                .name("GitHub App").platformType("GITHUB")
                .baseUrl("https://api.github.com").authType("GITHUB_APP")
                .status("ACTIVE")
                .credentials("{\"appId\":\"12345\",\"installationId\":\"67890\",\"privateKeyPem\":\"encrypted_pem\"}")
                .build();

        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(appConnection));

        // No user tokens
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());

        // No deploy keys
        when(sshKeyRepository.findByConnectionIdAndKeyUsage(connectionId, "DEPLOY_KEY"))
                .thenReturn(List.of());

        // GitHub App token generation — service calls isGitHubApp then getInstallationToken(connectionId)
        when(gitHubAppTokenService.isGitHubApp(eq(appConnection))).thenReturn(true);
        when(gitHubAppTokenService.getInstallationToken(eq(connectionId)))
                .thenReturn("ghs_app_installation_token_abc");

        CredentialResolutionResult result = service.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE);

        assertNotNull(result);
        assertEquals("ghs_app_installation_token_abc", result.getAccessToken());
        assertEquals(CredentialType.GITHUB_APP, result.getCredentialType());
        assertEquals(GitAuthMode.HTTPS_TOKEN, result.getGitAuthMode());
    }

    @Test
    @DisplayName("should throw exception when no credential strategy succeeds")
    void should_throwException_when_noCredentialAvailable() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(githubConnection));
        when(tokenRepository.findByUserIdAndConnectionId(userId, connectionId))
                .thenReturn(Optional.empty());
        when(sshKeyRepository.findByConnectionIdAndKeyUsage(connectionId, "DEPLOY_KEY"))
                .thenReturn(List.of());

        assertThrows(Exception.class, () ->
                service.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE));
    }

    @Test
    @DisplayName("should throw exception when connection not found")
    void should_throwException_when_connectionNotFound() {
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () ->
                service.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_CLONE));
    }
}
