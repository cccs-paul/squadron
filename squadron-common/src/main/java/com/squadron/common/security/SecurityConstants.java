package com.squadron.common.security;

/**
 * Security constants used across all Squadron modules.
 */
public final class SecurityConstants {

    private SecurityConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // HTTP Headers for identity propagation between services
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_AUTH_PROVIDER = "X-Auth-Provider";

    // Application roles
    public static final String ROLE_ADMIN = "squadron-admin";
    public static final String ROLE_TEAM_LEAD = "team-lead";
    public static final String ROLE_DEVELOPER = "developer";
    public static final String ROLE_QA = "qa";
    public static final String ROLE_VIEWER = "viewer";

    // JWT claim names
    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_USER_ID = "user_id";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_AUTH_PROVIDER = "auth_provider";
    public static final String CLAIM_DISPLAY_NAME = "display_name";

    // Auth provider types
    public static final String AUTH_PROVIDER_LDAP = "ldap";
    public static final String AUTH_PROVIDER_OIDC = "oidc";
    public static final String AUTH_PROVIDER_KEYCLOAK = "keycloak";

    // Token types
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
}
