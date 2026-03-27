package com.squadron.identity.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that collects all {@link AuthProvider} beans and provides lookup by provider type.
 */
@Component
public class AuthProviderRegistry {

    private final Map<String, AuthProvider> providerMap;

    public AuthProviderRegistry(List<AuthProvider> providers) {
        this.providerMap = new HashMap<>();
        for (AuthProvider provider : providers) {
            providerMap.put(provider.getProviderType(), provider);
        }
    }

    /**
     * Get an auth provider by type.
     *
     * @param providerType the provider type (e.g., "ldap", "oidc", "keycloak")
     * @return the auth provider, or null if not found
     */
    public AuthProvider getProvider(String providerType) {
        return providerMap.get(providerType);
    }

    /**
     * Check if a provider type is supported.
     *
     * @param providerType the provider type
     * @return true if the provider type is registered
     */
    public boolean isSupported(String providerType) {
        return providerMap.containsKey(providerType);
    }

    /**
     * @return all registered provider types
     */
    public java.util.Set<String> getSupportedTypes() {
        return providerMap.keySet();
    }
}
