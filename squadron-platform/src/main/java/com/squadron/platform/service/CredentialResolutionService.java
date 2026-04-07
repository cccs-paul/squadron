package com.squadron.platform.service;

import com.squadron.common.audit.AuditAction;
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
import com.squadron.platform.repository.UserPlatformTokenRepository;
import com.squadron.platform.repository.SshKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Central credential resolution service. Tries credential strategies in priority order:
 * 1. OAuth2 token (auto-refreshing)
 * 2. PAT
 * 3. Deploy Key (for git ops) + PAT/OAuth2 for API calls
 * 4. GitHub App installation token (GitHub only)
 */
@Service
@Transactional(readOnly = true)
public class CredentialResolutionService {

    private static final Logger log = LoggerFactory.getLogger(CredentialResolutionService.class);

    private final UserTokenService userTokenService;
    private final UserPlatformTokenRepository tokenRepository;
    private final SshKeyRepository sshKeyRepository;
    private final SshKeyService sshKeyService;
    private final PlatformConnectionRepository connectionRepository;
    private final GitHubAppTokenService gitHubAppTokenService;
    private final AuditService auditService;

    public CredentialResolutionService(UserTokenService userTokenService,
                                       UserPlatformTokenRepository tokenRepository,
                                       SshKeyRepository sshKeyRepository,
                                       SshKeyService sshKeyService,
                                       PlatformConnectionRepository connectionRepository,
                                       GitHubAppTokenService gitHubAppTokenService,
                                       AuditService auditService) {
        this.userTokenService = userTokenService;
        this.tokenRepository = tokenRepository;
        this.sshKeyRepository = sshKeyRepository;
        this.sshKeyService = sshKeyService;
        this.connectionRepository = connectionRepository;
        this.gitHubAppTokenService = gitHubAppTokenService;
        this.auditService = auditService;
    }

    /**
     * Resolve credentials for a user on a specific platform connection.
     * Tries strategies in priority order and returns the first successful result.
     *
     * @param userId       the user whose credentials to resolve
     * @param connectionId the platform connection
     * @param purpose      the intended use of the credentials
     * @return resolved credentials
     * @throws ResourceNotFoundException if no credentials could be resolved
     */
    public CredentialResolutionResult resolveCredentials(UUID userId, UUID connectionId, CredentialPurpose purpose) {
        log.info("Resolving credentials for user {} on connection {} for purpose {}", userId, connectionId, purpose);

        PlatformConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", connectionId));

        // Strategy 1: Try OAuth2 token
        CredentialResolutionResult oauth2Result = tryOAuth2Token(userId, connectionId);
        if (oauth2Result != null) {
            log.info("Resolved credentials via OAuth2 for user {} on connection {}", userId, connectionId);
            auditCredentialResolved(userId, connectionId, purpose, oauth2Result);
            return oauth2Result;
        }

        // Strategy 2: Try PAT
        CredentialResolutionResult patResult = tryPatToken(userId, connectionId);
        if (patResult != null) {
            log.info("Resolved credentials via PAT for user {} on connection {}", userId, connectionId);
            auditCredentialResolved(userId, connectionId, purpose, patResult);
            return patResult;
        }

        // Strategy 3: Try Deploy Key (for git operations only)
        if (isGitPurpose(purpose)) {
            CredentialResolutionResult deployKeyResult = tryDeployKey(connectionId, userId);
            if (deployKeyResult != null) {
                log.info("Resolved credentials via Deploy Key for connection {}", connectionId);
                auditCredentialResolved(userId, connectionId, purpose, deployKeyResult);
                return deployKeyResult;
            }
        }

        // Strategy 4: Try GitHub App installation token (GitHub connections only)
        if ("GITHUB".equalsIgnoreCase(connection.getPlatformType())) {
            CredentialResolutionResult appResult = tryGitHubAppToken(connection);
            if (appResult != null) {
                log.info("Resolved credentials via GitHub App for connection {}", connectionId);
                auditCredentialResolved(userId, connectionId, purpose, appResult);
                return appResult;
            }
        }

        // Audit the failure
        auditCredentialResolutionFailed(userId, connectionId, purpose);

        throw new ResourceNotFoundException("Credentials",
                "No credentials found for user " + userId + " on connection " + connectionId
                        + ". Please link your account via Settings > Platform Tokens.");
    }

    private CredentialResolutionResult tryOAuth2Token(UUID userId, UUID connectionId) {
        try {
            Optional<UserPlatformToken> tokenOpt = tokenRepository.findByUserIdAndConnectionId(userId, connectionId);
            if (tokenOpt.isPresent() && "oauth2".equals(tokenOpt.get().getTokenType())) {
                String decryptedToken = userTokenService.getDecryptedAccessToken(userId, connectionId);
                return CredentialResolutionResult.builder()
                        .accessToken(decryptedToken)
                        .credentialType(CredentialType.OAUTH2)
                        .expiresAt(tokenOpt.get().getExpiresAt())
                        .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                        .build();
            }
        } catch (Exception e) {
            log.debug("OAuth2 token resolution failed for user {} on connection {}: {}", userId, connectionId, e.getMessage());
        }
        return null;
    }

    private CredentialResolutionResult tryPatToken(UUID userId, UUID connectionId) {
        try {
            Optional<UserPlatformToken> tokenOpt = tokenRepository.findByUserIdAndConnectionId(userId, connectionId);
            if (tokenOpt.isPresent() && "pat".equals(tokenOpt.get().getTokenType())) {
                String decryptedToken = userTokenService.getDecryptedAccessToken(userId, connectionId);
                return CredentialResolutionResult.builder()
                        .accessToken(decryptedToken)
                        .credentialType(CredentialType.PAT)
                        .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                        .build();
            }
        } catch (Exception e) {
            log.debug("PAT resolution failed for user {} on connection {}: {}", userId, connectionId, e.getMessage());
        }
        return null;
    }

    private CredentialResolutionResult tryDeployKey(UUID connectionId, UUID userId) {
        try {
            List<SshKey> deployKeys = sshKeyRepository.findByConnectionIdAndKeyUsage(connectionId, "DEPLOY_KEY");
            if (!deployKeys.isEmpty()) {
                SshKey key = deployKeys.get(0);
                String privateKey = sshKeyService.getDecryptedPrivateKey(key.getId());

                // For deploy keys, we still need an API token for platform operations.
                // Try to get a PAT/OAuth2 token for API calls alongside the SSH key.
                String apiToken = null;
                try {
                    apiToken = userTokenService.getDecryptedAccessToken(userId, connectionId);
                } catch (Exception ignored) {
                    // Deploy key alone is sufficient for git operations
                }

                return CredentialResolutionResult.builder()
                        .accessToken(apiToken)
                        .sshPrivateKey(privateKey)
                        .credentialType(CredentialType.DEPLOY_KEY)
                        .gitAuthMode(GitAuthMode.SSH_KEY)
                        .build();
            }
        } catch (Exception e) {
            log.debug("Deploy key resolution failed for connection {}: {}", connectionId, e.getMessage());
        }
        return null;
    }

    private CredentialResolutionResult tryGitHubAppToken(PlatformConnection connection) {
        try {
            if (gitHubAppTokenService.isGitHubApp(connection)) {
                String token = gitHubAppTokenService.getInstallationToken(connection.getId());
                return CredentialResolutionResult.builder()
                        .accessToken(token)
                        .credentialType(CredentialType.GITHUB_APP)
                        .expiresAt(java.time.Instant.now().plusSeconds(3300))
                        .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                        .build();
            }
        } catch (Exception e) {
            log.debug("GitHub App token resolution failed for connection {}: {}", connection.getId(), e.getMessage());
        }
        return null;
    }

    /**
     * Audits a successful credential resolution event.
     */
    private void auditCredentialResolved(UUID userId, UUID connectionId,
                                          CredentialPurpose purpose, CredentialResolutionResult result) {
        try {
            String details = String.format(
                    "{\"connectionId\":\"%s\",\"purpose\":\"%s\",\"credentialType\":\"%s\",\"authMode\":\"%s\"}",
                    connectionId, purpose.name(), result.getCredentialType().name(), result.getGitAuthMode().name());
            auditService.logEvent(AuditEvent.builder()
                    .action("CREDENTIAL_RESOLVED")
                    .sourceService("platform-service")
                    .userId(userId)
                    .resourceType("PlatformConnection")
                    .resourceId(connectionId.toString())
                    .auditAction(AuditAction.READ)
                    .details(details)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to audit credential resolution: {}", e.getMessage());
        }
    }

    /**
     * Audits a failed credential resolution event.
     */
    private void auditCredentialResolutionFailed(UUID userId, UUID connectionId, CredentialPurpose purpose) {
        try {
            String details = String.format(
                    "{\"connectionId\":\"%s\",\"purpose\":\"%s\",\"reason\":\"No credential strategy succeeded\"}",
                    connectionId, purpose.name());
            auditService.logEvent(AuditEvent.builder()
                    .action("CREDENTIAL_RESOLUTION_FAILED")
                    .sourceService("platform-service")
                    .userId(userId)
                    .resourceType("PlatformConnection")
                    .resourceId(connectionId.toString())
                    .auditAction(AuditAction.READ)
                    .details(details)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to audit failed credential resolution: {}", e.getMessage());
        }
    }

    boolean isGitPurpose(CredentialPurpose purpose) {
        return purpose == CredentialPurpose.GIT_CLONE
                || purpose == CredentialPurpose.GIT_PUSH
                || purpose == CredentialPurpose.FULL;
        // REVIEW_BOT is intentionally excluded — bots use their own dedicated token
        // from ReviewBotConfig, not the credential resolution chain
    }
}
