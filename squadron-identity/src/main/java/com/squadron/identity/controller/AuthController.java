package com.squadron.identity.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.AuthTokenResponse;
import com.squadron.common.dto.LoginRequest;
import com.squadron.common.dto.OidcCallbackRequest;
import com.squadron.common.dto.RefreshTokenRequest;
import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.service.AuthProviderConfigService;
import com.squadron.identity.service.AuthService;
import com.squadron.identity.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for authentication endpoints.
 * All endpoints are PUBLIC (no auth required).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthProviderConfigService authProviderConfigService;
    private final TenantService tenantService;

    /**
     * LDAP/Keycloak password login.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthTokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * OIDC authorization code callback.
     */
    @PostMapping("/oidc/callback")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> oidcCallback(
            @Valid @RequestBody OidcCallbackRequest request,
            @RequestParam UUID tenantId) {
        AuthTokenResponse response = authService.oidcCallback(request, tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Refresh access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthTokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Public JWKS endpoint for token verification by other services.
     */
    @GetMapping("/jwks")
    public ResponseEntity<Map<String, Object>> getJwks() {
        return ResponseEntity.ok(authService.getJwks());
    }

    /**
     * List available auth providers for a tenant (public, for login UI).
     */
    @GetMapping("/providers/{tenantSlug}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProviders(@PathVariable String tenantSlug) {
        var tenant = tenantService.getTenantBySlug(tenantSlug);
        List<AuthProviderConfig> configs = authProviderConfigService.getEnabledConfigs(tenant.getId());

        // Return only non-sensitive provider info
        List<Map<String, Object>> providers = configs.stream()
                .map(c -> Map.<String, Object>of(
                        "name", c.getName(),
                        "type", c.getProviderType(),
                        "priority", c.getPriority()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(providers));
    }
}
