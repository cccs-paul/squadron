package com.squadron.common.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConstantsTest {

    // HTTP Header constants
    @Test
    void should_haveCorrectTenantIdHeader_when_accessed() {
        assertEquals("X-Tenant-Id", SecurityConstants.HEADER_TENANT_ID);
    }

    @Test
    void should_haveCorrectUserIdHeader_when_accessed() {
        assertEquals("X-User-Id", SecurityConstants.HEADER_USER_ID);
    }

    @Test
    void should_haveCorrectUserEmailHeader_when_accessed() {
        assertEquals("X-User-Email", SecurityConstants.HEADER_USER_EMAIL);
    }

    @Test
    void should_haveCorrectUserRolesHeader_when_accessed() {
        assertEquals("X-User-Roles", SecurityConstants.HEADER_USER_ROLES);
    }

    @Test
    void should_haveCorrectAuthProviderHeader_when_accessed() {
        assertEquals("X-Auth-Provider", SecurityConstants.HEADER_AUTH_PROVIDER);
    }

    // Application roles
    @Test
    void should_haveCorrectAdminRole_when_accessed() {
        assertEquals("squadron-admin", SecurityConstants.ROLE_ADMIN);
    }

    @Test
    void should_haveCorrectTeamLeadRole_when_accessed() {
        assertEquals("team-lead", SecurityConstants.ROLE_TEAM_LEAD);
    }

    @Test
    void should_haveCorrectDeveloperRole_when_accessed() {
        assertEquals("developer", SecurityConstants.ROLE_DEVELOPER);
    }

    @Test
    void should_haveCorrectQaRole_when_accessed() {
        assertEquals("qa", SecurityConstants.ROLE_QA);
    }

    @Test
    void should_haveCorrectViewerRole_when_accessed() {
        assertEquals("viewer", SecurityConstants.ROLE_VIEWER);
    }

    // JWT claim names
    @Test
    void should_haveCorrectTenantIdClaim_when_accessed() {
        assertEquals("tenant_id", SecurityConstants.CLAIM_TENANT_ID);
    }

    @Test
    void should_haveCorrectUserIdClaim_when_accessed() {
        assertEquals("user_id", SecurityConstants.CLAIM_USER_ID);
    }

    @Test
    void should_haveCorrectEmailClaim_when_accessed() {
        assertEquals("email", SecurityConstants.CLAIM_EMAIL);
    }

    @Test
    void should_haveCorrectRolesClaim_when_accessed() {
        assertEquals("roles", SecurityConstants.CLAIM_ROLES);
    }

    @Test
    void should_haveCorrectAuthProviderClaim_when_accessed() {
        assertEquals("auth_provider", SecurityConstants.CLAIM_AUTH_PROVIDER);
    }

    @Test
    void should_haveCorrectDisplayNameClaim_when_accessed() {
        assertEquals("display_name", SecurityConstants.CLAIM_DISPLAY_NAME);
    }

    // Auth provider types
    @Test
    void should_haveCorrectLdapProvider_when_accessed() {
        assertEquals("ldap", SecurityConstants.AUTH_PROVIDER_LDAP);
    }

    @Test
    void should_haveCorrectOidcProvider_when_accessed() {
        assertEquals("oidc", SecurityConstants.AUTH_PROVIDER_OIDC);
    }

    @Test
    void should_haveCorrectKeycloakProvider_when_accessed() {
        assertEquals("keycloak", SecurityConstants.AUTH_PROVIDER_KEYCLOAK);
    }

    // Token types
    @Test
    void should_haveCorrectAccessTokenType_when_accessed() {
        assertEquals("access", SecurityConstants.TOKEN_TYPE_ACCESS);
    }

    @Test
    void should_haveCorrectRefreshTokenType_when_accessed() {
        assertEquals("refresh", SecurityConstants.TOKEN_TYPE_REFRESH);
    }

    // Private constructor
    @Test
    void should_throwException_when_reflectiveInstantiationAttempted() throws NoSuchMethodException {
        Constructor<SecurityConstants> constructor = SecurityConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    void should_havePrivateConstructor_when_classExamined() throws NoSuchMethodException {
        Constructor<SecurityConstants> constructor = SecurityConstants.class.getDeclaredConstructor();

        assertFalse(constructor.canAccess(null));
    }
}
