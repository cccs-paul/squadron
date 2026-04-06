package com.squadron.common.security;

/**
 * The authentication mode for git operations.
 */
public enum GitAuthMode {
    /** HTTPS with token injected into URL (https://oauth2:TOKEN@host/repo.git) */
    HTTPS_TOKEN,
    /** SSH with private key file (GIT_SSH_COMMAND) */
    SSH_KEY
}
