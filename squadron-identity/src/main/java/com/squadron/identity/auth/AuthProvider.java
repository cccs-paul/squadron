package com.squadron.identity.auth;

import com.squadron.common.security.AuthenticationResult;
import com.squadron.identity.entity.AuthProviderConfig;

/**
 * Interface for authentication providers. Each implementation handles a specific
 * authentication mechanism (LDAP, OIDC, Keycloak).
 */
public interface AuthProvider {

    /**
     * @return The provider type identifier (e.g., "ldap", "oidc", "keycloak").
     */
    String getProviderType();

    /**
     * Authenticate a user with username and password.
     *
     * @param username the username or email
     * @param password the user's password
     * @param config   the auth provider configuration for the tenant
     * @return the authentication result containing user info and mapped roles
     * @throws com.squadron.identity.exception.AuthenticationException if authentication fails
     */
    AuthenticationResult authenticate(String username, String password, AuthProviderConfig config);

    /**
     * @param providerType the provider type to check
     * @return true if this provider handles the given type
     */
    boolean supports(String providerType);
}
