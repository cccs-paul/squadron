package com.squadron.common.security;

/**
 * The purpose for which credentials are being resolved.
 * Determines which credential strategies are applicable.
 */
public enum CredentialPurpose {
    /** Cloning a repository (git clone) — HTTPS token or SSH key */
    GIT_CLONE,
    /** Pushing to a repository (git push) — HTTPS token or SSH key */
    GIT_PUSH,
    /** Calling platform REST APIs (create PR, add comments, etc.) — HTTPS token only */
    PLATFORM_API,
    /** Full access: both git operations and platform API calls */
    FULL,
    /** Posting review comments as a bot user — uses the bot's own access token */
    REVIEW_BOT
}
