package com.squadron.identity.auth;

import com.squadron.common.security.AuthenticationResult;
import com.squadron.identity.entity.AuthProviderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthProviderTest {

    @Mock
    private AuthProvider authProvider;

    @Test
    void should_beAnInterface() {
        assertTrue(AuthProvider.class.isInterface());
    }

    @Test
    void should_declareAllExpectedMethods() {
        List<String> methodNames = Arrays.stream(AuthProvider.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertTrue(methodNames.contains("getProviderType"));
        assertTrue(methodNames.contains("authenticate"));
        assertTrue(methodNames.contains("supports"));
    }

    @Test
    void should_haveExactlyThreeMethods() {
        Method[] methods = AuthProvider.class.getDeclaredMethods();
        assertEquals(3, methods.length);
    }

    @Test
    void should_mockGetProviderType() {
        when(authProvider.getProviderType()).thenReturn("keycloak");

        assertEquals("keycloak", authProvider.getProviderType());
        verify(authProvider).getProviderType();
    }

    @Test
    void should_mockSupports() {
        when(authProvider.supports("keycloak")).thenReturn(true);
        when(authProvider.supports("ldap")).thenReturn(false);

        assertTrue(authProvider.supports("keycloak"));
        assertFalse(authProvider.supports("ldap"));
        verify(authProvider).supports("keycloak");
        verify(authProvider).supports("ldap");
    }

    @Test
    void should_mockAuthenticate() {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .providerType("oidc")
                .name("OIDC Provider")
                .config("{}")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AuthenticationResult expectedResult = AuthenticationResult.builder()
                .externalId("user-ext-123")
                .email("user@example.com")
                .displayName("Test User")
                .roles(Set.of("DEVELOPER"))
                .authProvider("oidc")
                .build();

        when(authProvider.authenticate("user@example.com", "password", config))
                .thenReturn(expectedResult);

        AuthenticationResult result = authProvider.authenticate("user@example.com", "password", config);

        assertNotNull(result);
        assertEquals("user-ext-123", result.getExternalId());
        assertEquals("user@example.com", result.getEmail());
        assertEquals("Test User", result.getDisplayName());
        assertTrue(result.getRoles().contains("DEVELOPER"));
        assertEquals("oidc", result.getAuthProvider());
        verify(authProvider).authenticate("user@example.com", "password", config);
    }

    @Test
    void should_allowAnonymousImplementation() {
        AuthProvider anonymous = new AuthProvider() {
            @Override
            public String getProviderType() { return "custom"; }

            @Override
            public AuthenticationResult authenticate(String username, String password, AuthProviderConfig config) {
                return AuthenticationResult.builder()
                        .externalId("custom-" + username)
                        .email(username)
                        .displayName(username)
                        .authProvider("custom")
                        .build();
            }

            @Override
            public boolean supports(String providerType) {
                return "custom".equals(providerType);
            }
        };

        assertEquals("custom", anonymous.getProviderType());
        assertTrue(anonymous.supports("custom"));
        assertFalse(anonymous.supports("ldap"));

        AuthenticationResult result = anonymous.authenticate("testuser", "pass", null);
        assertEquals("custom-testuser", result.getExternalId());
        assertEquals("testuser", result.getEmail());
    }

    @Test
    void should_haveCorrectMethodSignatures() throws NoSuchMethodException {
        Method getProviderType = AuthProvider.class.getDeclaredMethod("getProviderType");
        assertEquals(String.class, getProviderType.getReturnType());
        assertEquals(0, getProviderType.getParameterCount());

        Method authenticate = AuthProvider.class.getDeclaredMethod("authenticate",
                String.class, String.class, AuthProviderConfig.class);
        assertEquals(AuthenticationResult.class, authenticate.getReturnType());
        assertEquals(3, authenticate.getParameterCount());

        Method supports = AuthProvider.class.getDeclaredMethod("supports", String.class);
        assertEquals(boolean.class, supports.getReturnType());
        assertEquals(1, supports.getParameterCount());
    }
}
