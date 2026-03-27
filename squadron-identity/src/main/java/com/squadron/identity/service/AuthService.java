package com.squadron.identity.service;

import com.squadron.common.dto.AuthTokenResponse;
import com.squadron.common.dto.LoginRequest;
import com.squadron.common.dto.OidcCallbackRequest;
import com.squadron.common.dto.RefreshTokenRequest;
import com.squadron.common.dto.UserDto;
import com.squadron.common.security.AuthenticationResult;
import com.squadron.common.security.SecurityConstants;
import com.squadron.common.security.SquadronJwtService;
import com.squadron.identity.auth.AuthProvider;
import com.squadron.identity.auth.AuthProviderRegistry;
import com.squadron.identity.auth.OidcAuthProvider;
import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.entity.Tenant;
import com.squadron.identity.entity.User;
import com.squadron.identity.exception.AuthenticationException;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.AuthProviderConfigRepository;
import com.squadron.identity.repository.TenantRepository;
import com.squadron.identity.repository.UserRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main authentication service that orchestrates the auth flow:
 * 1. Looks up enabled auth provider configs for a tenant (ordered by priority)
 * 2. Attempts authentication against each provider in order
 * 3. On success, auto-provisions or updates the user record
 * 4. Issues Squadron JWT tokens (access + refresh)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthProviderRegistry authProviderRegistry;
    private final AuthProviderConfigRepository authProviderConfigRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SquadronJwtService jwtService;

    /**
     * Authenticate with username/password (LDAP or Keycloak).
     */
    public AuthTokenResponse login(LoginRequest request) {
        // Resolve tenant
        Tenant tenant = resolveTenant(request.getTenantSlug());
        UUID tenantId = tenant.getId();

        // Get enabled auth provider configs for this tenant, ordered by priority
        List<AuthProviderConfig> configs = authProviderConfigRepository
                .findByTenantIdAndEnabledOrderByPriorityAsc(tenantId, true);

        if (configs.isEmpty()) {
            throw new AuthenticationException("No authentication providers configured for tenant: " + tenant.getSlug());
        }

        // Try each provider in order
        AuthenticationException lastException = null;
        for (AuthProviderConfig config : configs) {
            // Skip OIDC providers (they don't support password auth)
            if (SecurityConstants.AUTH_PROVIDER_OIDC.equals(config.getProviderType())) {
                continue;
            }

            AuthProvider provider = authProviderRegistry.getProvider(config.getProviderType());
            if (provider == null) {
                log.warn("No provider implementation found for type: {}", config.getProviderType());
                continue;
            }

            try {
                AuthenticationResult result = provider.authenticate(
                        request.getUsername(), request.getPassword(), config);

                // Auto-provision or update user
                User user = provisionUser(result, tenantId);

                // Generate tokens
                return buildTokenResponse(user);

            } catch (AuthenticationException e) {
                lastException = e;
                log.debug("Authentication failed with provider {} ({}): {}",
                        config.getName(), config.getProviderType(), e.getMessage());
            }
        }

        throw lastException != null ? lastException
                : new AuthenticationException("Authentication failed: no suitable provider found");
    }

    /**
     * Handle OIDC authorization code callback.
     */
    public AuthTokenResponse oidcCallback(OidcCallbackRequest request, UUID tenantId) {
        List<AuthProviderConfig> configs = authProviderConfigRepository
                .findByTenantIdAndProviderType(tenantId, SecurityConstants.AUTH_PROVIDER_OIDC);

        if (configs.isEmpty()) {
            throw new AuthenticationException("No OIDC provider configured for tenant");
        }

        AuthProviderConfig config = configs.stream()
                .filter(AuthProviderConfig::isEnabled)
                .findFirst()
                .orElseThrow(() -> new AuthenticationException("No enabled OIDC provider found"));

        AuthProvider provider = authProviderRegistry.getProvider(SecurityConstants.AUTH_PROVIDER_OIDC);
        if (!(provider instanceof OidcAuthProvider oidcProvider)) {
            throw new AuthenticationException("OIDC provider implementation not available");
        }

        AuthenticationResult result = oidcProvider.authenticateWithCode(
                request.getCode(), request.getRedirectUri(), config);

        User user = provisionUser(result, tenantId);
        return buildTokenResponse(user);
    }

    /**
     * Refresh an access token using a valid refresh token.
     */
    public AuthTokenResponse refreshToken(RefreshTokenRequest request) {
        try {
            JWTClaimsSet claims = jwtService.validateToken(request.getRefreshToken());

            String tokenType = claims.getStringClaim("token_type");
            if (!SecurityConstants.TOKEN_TYPE_REFRESH.equals(tokenType)) {
                throw new AuthenticationException("Invalid token type: expected refresh token");
            }

            UUID userId = UUID.fromString(claims.getSubject());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AuthenticationException("User not found"));

            return buildTokenResponse(user);

        } catch (SecurityException e) {
            throw new AuthenticationException("Invalid refresh token: " + e.getMessage());
        } catch (ParseException e) {
            throw new AuthenticationException("Failed to parse refresh token");
        }
    }

    /**
     * Get public JWKS for token verification by other services.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getJwks() {
        return jwtService.getPublicJwks();
    }

    /**
     * Auto-provision or update a user based on authentication result.
     */
    private User provisionUser(AuthenticationResult result, UUID tenantId) {
        // Try to find existing user by externalId + tenantId
        User user = userRepository.findByExternalIdAndTenantId(result.getExternalId(), tenantId)
                .orElse(null);

        if (user == null) {
            // Try by email + tenantId
            user = userRepository.findByEmailAndTenantId(result.getEmail(), tenantId)
                    .orElse(null);
        }

        Set<String> roles = result.getRoles() != null ? result.getRoles() : Set.of(SecurityConstants.ROLE_DEVELOPER);
        String primaryRole = roles.iterator().next().toUpperCase();

        if (user == null) {
            // Create new user
            user = User.builder()
                    .tenantId(tenantId)
                    .externalId(result.getExternalId())
                    .email(result.getEmail())
                    .displayName(result.getDisplayName())
                    .role(primaryRole)
                    .authProvider(result.getAuthProvider())
                    .roles(roles)
                    .build();
            user = userRepository.save(user);
            log.info("Auto-provisioned new user: {} ({})", user.getEmail(), user.getExternalId());
        } else {
            // Update existing user if needed
            boolean changed = false;
            if (result.getEmail() != null && !result.getEmail().equals(user.getEmail())) {
                user.setEmail(result.getEmail());
                changed = true;
            }
            if (result.getDisplayName() != null && !result.getDisplayName().equals(user.getDisplayName())) {
                user.setDisplayName(result.getDisplayName());
                changed = true;
            }
            if (!primaryRole.equals(user.getRole())) {
                user.setRole(primaryRole);
                changed = true;
            }
            if (result.getExternalId() != null && !result.getExternalId().equals(user.getExternalId())) {
                user.setExternalId(result.getExternalId());
                changed = true;
            }
            if (!roles.equals(user.getRoles())) {
                user.setRoles(roles);
                changed = true;
            }
            if (changed) {
                user = userRepository.save(user);
                log.info("Updated provisioned user: {} ({})", user.getEmail(), user.getExternalId());
            }
        }

        return user;
    }

    private AuthTokenResponse buildTokenResponse(User user) {
        Set<String> roles = user.getRoles() != null ? user.getRoles() : Set.of(user.getRole());

        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getTenantId(), user.getEmail(),
                user.getDisplayName(), roles, user.getAuthProvider());

        String refreshToken = jwtService.generateRefreshToken(
                user.getId(), user.getTenantId(), user.getEmail(),
                user.getDisplayName(), roles, user.getAuthProvider());

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .externalId(user.getExternalId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .authProvider(user.getAuthProvider())
                .roles(roles)
                .build();

        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600) // 1 hour in seconds
                .user(userDto)
                .build();
    }

    private Tenant resolveTenant(String tenantSlug) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            // Try to find a default tenant or the only tenant
            List<Tenant> tenants = tenantRepository.findByStatus("ACTIVE");
            if (tenants.size() == 1) {
                return tenants.get(0);
            }
            throw new AuthenticationException("Tenant slug is required for multi-tenant authentication");
        }
        return tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "slug", tenantSlug));
    }
}
