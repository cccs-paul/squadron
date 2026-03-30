package com.squadron.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.dto.AuthTokenResponse;
import com.squadron.common.dto.LoginRequest;
import com.squadron.common.dto.OidcCallbackRequest;
import com.squadron.common.dto.RefreshTokenRequest;
import com.squadron.common.dto.UserDto;
import com.squadron.common.dto.TenantDto;
import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.exception.AuthenticationException;
import com.squadron.identity.exception.GlobalExceptionHandler;
import com.squadron.identity.service.AuthProviderConfigService;
import com.squadron.identity.service.AuthService;
import com.squadron.identity.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @Mock
    private AuthProviderConfigService authProviderConfigService;

    @Mock
    private TenantService tenantService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void should_returnToken_when_loginSuccessful() throws Exception {
        AuthTokenResponse tokenResponse = AuthTokenResponse.builder()
                .accessToken("access-token-123")
                .refreshToken("refresh-token-456")
                .tokenType("Bearer")
                .expiresIn(3600)
                .user(UserDto.builder().email("user@example.com").build())
                .build();
        when(authService.login(any(LoginRequest.class))).thenReturn(tokenResponse);

        LoginRequest request = LoginRequest.builder()
                .username("user")
                .password("pass")
                .tenantSlug("acme")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-456"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void should_return401_when_loginFails() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AuthenticationException("Invalid credentials"));

        LoginRequest request = LoginRequest.builder()
                .username("user")
                .password("wrong")
                .tenantSlug("acme")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_returnToken_when_oidcCallbackSuccessful() throws Exception {
        UUID tenantId = UUID.randomUUID();
        AuthTokenResponse tokenResponse = AuthTokenResponse.builder()
                .accessToken("oidc-access-token")
                .refreshToken("oidc-refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600)
                .build();
        when(authService.oidcCallback(any(OidcCallbackRequest.class), eq(tenantId))).thenReturn(tokenResponse);

        OidcCallbackRequest request = OidcCallbackRequest.builder()
                .code("auth-code-123")
                .state("state-xyz")
                .redirectUri("http://localhost/callback")
                .build();

        mockMvc.perform(post("/api/auth/oidc/callback")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("oidc-access-token"));
    }

    @Test
    void should_returnToken_when_refreshSuccessful() throws Exception {
        AuthTokenResponse tokenResponse = AuthTokenResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600)
                .build();
        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(tokenResponse);

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("old-refresh-token")
                .build();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
    }

    @Test
    void should_returnJwks_when_jwksEndpointCalled() throws Exception {
        Map<String, Object> jwks = Map.of("keys", List.of(Map.of("kty", "RSA", "kid", "key1")));
        when(authService.getJwks()).thenReturn(jwks);

        mockMvc.perform(get("/api/auth/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    @Test
    void should_returnProviders_when_tenantSlugExists() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantDto tenant = TenantDto.builder().id(tenantId).name("Acme").slug("acme").build();
        when(tenantService.getTenantBySlug("acme")).thenReturn(tenant);

        AuthProviderConfig config1 = AuthProviderConfig.builder()
                .name("Corporate LDAP")
                .providerType("ldap")
                .priority(1)
                .build();
        AuthProviderConfig config2 = AuthProviderConfig.builder()
                .name("Google SSO")
                .providerType("oidc")
                .priority(2)
                .build();
        when(authProviderConfigService.getEnabledConfigs(tenantId)).thenReturn(List.of(config1, config2));

        mockMvc.perform(get("/api/auth/providers/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Corporate LDAP"))
                .andExpect(jsonPath("$.data[0].type").value("ldap"))
                .andExpect(jsonPath("$.data[1].name").value("Google SSO"));
    }

    @Test
    void should_returnEmptyList_when_noProvidersConfigured() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantDto tenant = TenantDto.builder().id(tenantId).slug("empty").build();
        when(tenantService.getTenantBySlug("empty")).thenReturn(tenant);
        when(authProviderConfigService.getEnabledConfigs(tenantId)).thenReturn(List.of());

        mockMvc.perform(get("/api/auth/providers/empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void should_returnTenants_when_tenantsEndpointCalled() throws Exception {
        UUID tenantId1 = UUID.randomUUID();
        UUID tenantId2 = UUID.randomUUID();
        TenantDto tenant1 = TenantDto.builder().id(tenantId1).name("Planet Express").slug("planet-express").build();
        TenantDto tenant2 = TenantDto.builder().id(tenantId2).name("Mom Corp").slug("mom-corp").build();
        when(tenantService.listActiveTenants()).thenReturn(List.of(tenant1, tenant2));

        mockMvc.perform(get("/api/auth/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Planet Express"))
                .andExpect(jsonPath("$.data[0].slug").value("planet-express"))
                .andExpect(jsonPath("$.data[0].id").value(tenantId1.toString()))
                .andExpect(jsonPath("$.data[1].name").value("Mom Corp"))
                .andExpect(jsonPath("$.data[1].slug").value("mom-corp"));
    }

    @Test
    void should_returnEmptyList_when_noActiveTenants() throws Exception {
        when(tenantService.listActiveTenants()).thenReturn(List.of());

        mockMvc.perform(get("/api/auth/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
