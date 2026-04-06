package com.squadron.common.dto;

import com.squadron.common.security.CredentialType;
import com.squadron.common.security.GitAuthMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result of credential resolution. Contains the access token and/or SSH key
 * needed to perform git operations or platform API calls on behalf of a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialResolutionResult {

    /** The access token (OAuth2, PAT, or GitHub App installation token) */
    private String accessToken;

    /** SSH private key (only populated when gitAuthMode is SSH_KEY) */
    private String sshPrivateKey;

    /** The type of credential that was resolved */
    private CredentialType credentialType;

    /** When the credential expires (null for non-expiring PATs) */
    private Instant expiresAt;

    /** The authentication mode for git operations */
    private GitAuthMode gitAuthMode;
}
