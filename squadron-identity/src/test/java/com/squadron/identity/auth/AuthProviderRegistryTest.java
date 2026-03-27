package com.squadron.identity.auth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthProviderRegistryTest {

    @Test
    void should_registerProviders_when_constructed() {
        AuthProvider ldap = mock(AuthProvider.class);
        AuthProvider oidc = mock(AuthProvider.class);
        AuthProvider keycloak = mock(AuthProvider.class);

        when(ldap.getProviderType()).thenReturn("ldap");
        when(oidc.getProviderType()).thenReturn("oidc");
        when(keycloak.getProviderType()).thenReturn("keycloak");

        AuthProviderRegistry registry = new AuthProviderRegistry(List.of(ldap, oidc, keycloak));

        assertEquals(ldap, registry.getProvider("ldap"));
        assertEquals(oidc, registry.getProvider("oidc"));
        assertEquals(keycloak, registry.getProvider("keycloak"));
    }

    @Test
    void should_returnNull_when_providerTypeNotRegistered() {
        AuthProviderRegistry registry = new AuthProviderRegistry(List.of());

        assertNull(registry.getProvider("unknown"));
    }

    @Test
    void should_returnTrue_when_providerTypeIsSupported() {
        AuthProvider ldap = mock(AuthProvider.class);
        when(ldap.getProviderType()).thenReturn("ldap");

        AuthProviderRegistry registry = new AuthProviderRegistry(List.of(ldap));

        assertTrue(registry.isSupported("ldap"));
        assertFalse(registry.isSupported("unknown"));
    }

    @Test
    void should_returnAllSupportedTypes_when_getSupportedTypesCalled() {
        AuthProvider ldap = mock(AuthProvider.class);
        AuthProvider oidc = mock(AuthProvider.class);
        when(ldap.getProviderType()).thenReturn("ldap");
        when(oidc.getProviderType()).thenReturn("oidc");

        AuthProviderRegistry registry = new AuthProviderRegistry(List.of(ldap, oidc));

        Set<String> types = registry.getSupportedTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains("ldap"));
        assertTrue(types.contains("oidc"));
    }

    @Test
    void should_handleEmptyProviderList_when_constructed() {
        AuthProviderRegistry registry = new AuthProviderRegistry(List.of());

        assertTrue(registry.getSupportedTypes().isEmpty());
        assertFalse(registry.isSupported("ldap"));
    }
}
