package com.squadron.identity.service;

import com.squadron.common.dto.AuthTokenResponse;
import com.squadron.common.dto.LoginRequest;
import com.squadron.common.dto.RefreshTokenRequest;
import com.squadron.common.security.AuthenticationResult;
import com.squadron.common.security.SecurityConstants;
import com.squadron.common.security.SquadronJwtService;
import com.squadron.identity.auth.AuthProvider;
import com.squadron.identity.auth.AuthProviderRegistry;
import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.entity.Tenant;
import com.squadron.identity.entity.User;
import com.squadron.identity.exception.AuthenticationException;
import com.squadron.identity.repository.AuthProviderConfigRepository;
import com.squadron.identity.repository.TenantRepository;
import com.squadron.identity.repository.UserRepository;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthProviderRegistry authProviderRegistry;
    @Mock
    private AuthProviderConfigRepository authProviderConfigRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SquadronJwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private UUID tenantId;
    private Tenant tenant;
    private AuthProviderConfig ldapConfig;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder()
                .id(tenantId)
                .name("Test Org")
                .slug("test-org")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ldapConfig = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .providerType(SecurityConstants.AUTH_PROVIDER_LDAP)
                .name("Corporate LDAP")
                .enabled(true)
                .priority(0)
                .config("{}")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void should_login_when_validCredentials() {
        LoginRequest request = LoginRequest.builder()
                .username("jdoe")
                .password("secret")
                .tenantSlug("test-org")
                .build();

        AuthenticationResult authResult = AuthenticationResult.builder()
                .externalId("cn=jdoe,dc=example,dc=com")
                .email("jdoe@example.com")
                .displayName("John Doe")
                .roles(Set.of(SecurityConstants.ROLE_DEVELOPER))
                .authProvider(SecurityConstants.AUTH_PROVIDER_LDAP)
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalId("cn=jdoe,dc=example,dc=com")
                .email("jdoe@example.com")
                .displayName("John Doe")
                .role("DEVELOPER")
                .authProvider("ldap")
                .roles(Set.of(SecurityConstants.ROLE_DEVELOPER))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AuthProvider provider = mock(AuthProvider.class);

        when(tenantRepository.findBySlug("test-org")).thenReturn(Optional.of(tenant));
        when(authProviderConfigRepository.findByTenantIdAndEnabledOrderByPriorityAsc(tenantId, true))
                .thenReturn(List.of(ldapConfig));
        when(authProviderRegistry.getProvider(SecurityConstants.AUTH_PROVIDER_LDAP)).thenReturn(provider);
        when(provider.authenticate("jdoe", "secret", ldapConfig)).thenReturn(authResult);
        when(userRepository.findByExternalIdAndTenantId("cn=jdoe,dc=example,dc=com", tenantId))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailAndTenantId("jdoe@example.com", tenantId))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateAccessToken(any(), any(), any(), any(), any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(), any(), any(), any(), any(), any())).thenReturn("refresh-token");

        AuthTokenResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getUser());
        assertEquals("jdoe@example.com", response.getUser().getEmail());
    }

    @Test
    void should_throwAuthException_when_noProvidersConfigured() {
        LoginRequest request = LoginRequest.builder()
                .username("jdoe")
                .password("secret")
                .tenantSlug("test-org")
                .build();

        when(tenantRepository.findBySlug("test-org")).thenReturn(Optional.of(tenant));
        when(authProviderConfigRepository.findByTenantIdAndEnabledOrderByPriorityAsc(tenantId, true))
                .thenReturn(List.of());

        assertThrows(AuthenticationException.class, () -> authService.login(request));
    }

    @Test
    void should_skipOidcProviders_when_loginCalledWithPassword() {
        LoginRequest request = LoginRequest.builder()
                .username("jdoe")
                .password("secret")
                .tenantSlug("test-org")
                .build();

        AuthProviderConfig oidcConfig = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .providerType(SecurityConstants.AUTH_PROVIDER_OIDC)
                .name("OIDC")
                .enabled(true)
                .priority(0)
                .config("{}")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(tenantRepository.findBySlug("test-org")).thenReturn(Optional.of(tenant));
        when(authProviderConfigRepository.findByTenantIdAndEnabledOrderByPriorityAsc(tenantId, true))
                .thenReturn(List.of(oidcConfig));

        // Should throw because the only provider (OIDC) is skipped
        assertThrows(AuthenticationException.class, () -> authService.login(request));
        verify(authProviderRegistry, never()).getProvider(SecurityConstants.AUTH_PROVIDER_OIDC);
    }

    @Test
    void should_resolveSingleTenant_when_noTenantSlugProvided() {
        LoginRequest request = LoginRequest.builder()
                .username("jdoe")
                .password("secret")
                .build();

        when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of(tenant));
        when(authProviderConfigRepository.findByTenantIdAndEnabledOrderByPriorityAsc(tenantId, true))
                .thenReturn(List.of());

        // Will throw because no providers configured, but tenant resolution should work
        assertThrows(AuthenticationException.class, () -> authService.login(request));
        verify(tenantRepository).findByStatus("ACTIVE");
    }

    @Test
    void should_throwAuthException_when_multipleTenantAndNoSlug() {
        LoginRequest request = LoginRequest.builder()
                .username("jdoe")
                .password("secret")
                .build();

        Tenant tenant2 = Tenant.builder().id(UUID.randomUUID()).name("Other").slug("other").status("ACTIVE").build();
        when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of(tenant, tenant2));

        assertThrows(AuthenticationException.class, () -> authService.login(request));
    }

    @Test
    void should_updateExistingUser_when_provisioningAndUserExists() {
        LoginRequest request = LoginRequest.builder()
                .username("jdoe")
                .password("secret")
                .tenantSlug("test-org")
                .build();

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalId("cn=jdoe,dc=example,dc=com")
                .email("old@example.com")
                .displayName("Old Name")
                .role("DEVELOPER")
                .authProvider("ldap")
                .roles(Set.of("developer"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AuthenticationResult authResult = AuthenticationResult.builder()
                .externalId("cn=jdoe,dc=example,dc=com")
                .email("new@example.com")
                .displayName("New Name")
                .roles(Set.of(SecurityConstants.ROLE_DEVELOPER))
                .authProvider(SecurityConstants.AUTH_PROVIDER_LDAP)
                .build();

        AuthProvider provider = mock(AuthProvider.class);

        when(tenantRepository.findBySlug("test-org")).thenReturn(Optional.of(tenant));
        when(authProviderConfigRepository.findByTenantIdAndEnabledOrderByPriorityAsc(tenantId, true))
                .thenReturn(List.of(ldapConfig));
        when(authProviderRegistry.getProvider(SecurityConstants.AUTH_PROVIDER_LDAP)).thenReturn(provider);
        when(provider.authenticate("jdoe", "secret", ldapConfig)).thenReturn(authResult);
        when(userRepository.findByExternalIdAndTenantId("cn=jdoe,dc=example,dc=com", tenantId))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(jwtService.generateAccessToken(any(), any(), any(), any(), any(), any())).thenReturn("access");
        when(jwtService.generateRefreshToken(any(), any(), any(), any(), any(), any())).thenReturn("refresh");

        AuthTokenResponse response = authService.login(request);

        assertNotNull(response);
        verify(userRepository).save(existingUser);
        assertEquals("new@example.com", existingUser.getEmail());
        assertEquals("New Name", existingUser.getDisplayName());
    }

    @Test
    void should_refreshToken_when_validRefreshToken() throws Exception {
        UUID userId = UUID.randomUUID();
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("valid-refresh-token")
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .claim("token_type", SecurityConstants.TOKEN_TYPE_REFRESH)
                .build();

        User user = User.builder()
                .id(userId)
                .tenantId(tenantId)
                .externalId("ext-id")
                .email("test@example.com")
                .displayName("Test")
                .role("DEVELOPER")
                .authProvider("ldap")
                .roles(Set.of("developer"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(jwtService.validateToken("valid-refresh-token")).thenReturn(claims);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(any(), any(), any(), any(), any(), any())).thenReturn("new-access");
        when(jwtService.generateRefreshToken(any(), any(), any(), any(), any(), any())).thenReturn("new-refresh");

        AuthTokenResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new-access", response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());
    }

    @Test
    void should_throwAuthException_when_refreshTokenIsAccessToken() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("access-token-used-as-refresh")
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("token_type", SecurityConstants.TOKEN_TYPE_ACCESS)
                .build();

        when(jwtService.validateToken(anyString())).thenReturn(claims);

        assertThrows(AuthenticationException.class, () -> authService.refreshToken(request));
    }

    @Test
    void should_throwAuthException_when_refreshTokenUserNotFound() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("valid-refresh-token")
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("token_type", SecurityConstants.TOKEN_TYPE_REFRESH)
                .build();

        when(jwtService.validateToken(anyString())).thenReturn(claims);
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(AuthenticationException.class, () -> authService.refreshToken(request));
    }

    @Test
    void should_returnJwks_when_getJwksCalled() {
        Map<String, Object> expectedJwks = Map.of("keys", List.of());
        when(jwtService.getPublicJwks()).thenReturn(expectedJwks);

        Map<String, Object> jwks = authService.getJwks();

        assertEquals(expectedJwks, jwks);
    }
}
