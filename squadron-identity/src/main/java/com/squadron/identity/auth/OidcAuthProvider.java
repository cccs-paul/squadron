package com.squadron.identity.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.squadron.common.security.AuthenticationResult;
import com.squadron.common.security.SecurityConstants;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.common.util.JsonUtils;
import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.exception.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenID Connect authentication provider.
 * Handles authorization code exchange and user info extraction from ID tokens.
 */
@Component
public class OidcAuthProvider implements AuthProvider {

    private static final Logger log = LoggerFactory.getLogger(OidcAuthProvider.class);

    private final WebClient webClient;
    private final TokenEncryptionService tokenEncryptionService;

    public OidcAuthProvider(WebClient.Builder webClientBuilder,
                            TokenEncryptionService tokenEncryptionService) {
        this.webClient = webClientBuilder.build();
        this.tokenEncryptionService = tokenEncryptionService;
    }

    @Override
    public String getProviderType() {
        return SecurityConstants.AUTH_PROVIDER_OIDC;
    }

    @Override
    public boolean supports(String providerType) {
        return SecurityConstants.AUTH_PROVIDER_OIDC.equals(providerType);
    }

    @Override
    public AuthenticationResult authenticate(String username, String password, AuthProviderConfig config) {
        throw new UnsupportedOperationException(
                "OIDC does not support username/password authentication. Use authenticateWithCode() instead.");
    }

    /**
     * Authenticate via OIDC authorization code exchange.
     *
     * @param code        the authorization code from the OIDC provider
     * @param redirectUri the redirect URI used in the authorization request
     * @param config      the auth provider configuration
     * @return the authentication result
     */
    public AuthenticationResult authenticateWithCode(String code, String redirectUri, AuthProviderConfig config) {
        Map<String, Object> configMap = parseConfig(config.getConfig());

        String tokenEndpoint = getString(configMap, "tokenEndpoint");
        String clientId = getString(configMap, "clientId");
        String clientSecret = getString(configMap, "clientSecret");
        String usernameClaim = getString(configMap, "usernameClaim", "email");
        String emailClaim = getString(configMap, "emailClaim", "email");
        String rolesClaim = getString(configMap, "rolesClaim", "groups");

        // Decrypt client secret if encrypted
        if (!clientSecret.isEmpty()) {
            try {
                clientSecret = tokenEncryptionService.decrypt(clientSecret);
            } catch (Exception e) {
                log.debug("Client secret does not appear to be encrypted, using as-is");
            }
        }

        try {
            // Exchange authorization code for tokens
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");
            formData.add("code", code);
            formData.add("redirect_uri", redirectUri);
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);

            Map<String, Object> tokenResponse = webClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (tokenResponse == null || !tokenResponse.containsKey("id_token")) {
                throw new AuthenticationException("OIDC token exchange failed: no id_token in response");
            }

            // Parse the ID token (JWT) to extract claims
            String idToken = tokenResponse.get("id_token").toString();
            Map<String, Object> claims = parseJwtPayload(idToken);

            // Extract user info from claims
            String subject = getString(claims, "sub");
            String email = getString(claims, emailClaim, getString(claims, "email", ""));
            String username = getString(claims, usernameClaim, email);
            String displayName = getString(claims, "name",
                    getString(claims, "preferred_username", username));

            // Extract roles from configured claim
            Set<String> roles = extractRoles(claims, rolesClaim);
            if (roles.isEmpty()) {
                roles.add(SecurityConstants.ROLE_DEVELOPER);
            }

            return AuthenticationResult.builder()
                    .externalId(subject)
                    .email(email)
                    .displayName(displayName)
                    .roles(roles)
                    .authProvider(SecurityConstants.AUTH_PROVIDER_OIDC)
                    .attributes(Map.of(
                            "subject", subject,
                            "idToken", idToken,
                            "accessToken", tokenResponse.getOrDefault("access_token", "").toString()
                    ))
                    .build();

        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("OIDC authentication failed", e);
            throw new AuthenticationException("OIDC authentication failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJwtPayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                throw new AuthenticationException("Invalid JWT format");
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return JsonUtils.fromJson(payload, new TypeReference<Map<String, Object>>() {});
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthenticationException("Failed to parse ID token: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractRoles(Map<String, Object> claims, String rolesClaim) {
        Set<String> roles = new HashSet<>();
        Object rolesValue = claims.get(rolesClaim);
        if (rolesValue instanceof List) {
            for (Object role : (List<Object>) rolesValue) {
                roles.add(role.toString());
            }
        } else if (rolesValue instanceof String) {
            String rolesStr = (String) rolesValue;
            for (String role : rolesStr.split("[,\\s]+")) {
                if (!role.isBlank()) {
                    roles.add(role.trim());
                }
            }
        }
        return roles;
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Collections.emptyMap();
        }
        return JsonUtils.fromJson(configJson, new TypeReference<Map<String, Object>>() {});
    }

    private String getString(Map<String, Object> map, String key) {
        return getString(map, key, "");
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
