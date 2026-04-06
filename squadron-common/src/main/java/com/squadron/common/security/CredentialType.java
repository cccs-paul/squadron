package com.squadron.common.security;

/**
 * The type of credential that was resolved.
 */
public enum CredentialType {
    /** OAuth2 access token (auto-refreshable) */
    OAUTH2,
    /** Personal Access Token */
    PAT,
    /** Deploy key (SSH-based, repo-scoped) */
    DEPLOY_KEY,
    /** GitHub App installation token (short-lived) */
    GITHUB_APP
}
