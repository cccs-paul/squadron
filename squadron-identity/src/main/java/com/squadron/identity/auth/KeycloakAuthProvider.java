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
 * Keycloak authentication provider.
 * Authenticates via Keycloak's token endpoint using resource owner password grant
 * and extracts roles from the JWT.
 */
@Component
public class KeycloakAuthProvider implements AuthProvider {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthProvider.class);

    private final WebClient webClient;
    private final TokenEncryptionService tokenEncryptionService;

    public KeycloakAuthProvider(WebClient.Builder webClientBuilder,
                                TokenEncryptionService tokenEncryptionService) {
        this.webClient = webClientBuilder.build();
        this.tokenEncryptionService = tokenEncryptionService;
    }

    @Override
    public String getProviderType() {
        return SecurityConstants.AUTH_PROVIDER_KEYCLOAK;
    }

    @Override
    public boolean supports(String providerType) {
        return SecurityConstants.AUTH_PROVIDER_KEYCLOAK.equals(providerType);
    }

    @Override
    public AuthenticationResult authenticate(String username, String password, AuthProviderConfig config) {
        Map<String, Object> configMap = parseConfig(config.getConfig());

        String serverUrl = getString(configMap, "serverUrl");
        String realm = getString(configMap, "realm");
        String clientId = getString(configMap, "clientId");
        String clientSecret = getString(configMap, "clientSecret");

        // Decrypt client secret if encrypted
        if (!clientSecret.isEmpty()) {
            try {
                clientSecret = tokenEncryptionService.decrypt(clientSecret);
            } catch (Exception e) {
                log.debug("Client secret does not appear to be encrypted, using as-is");
            }
        }

        String tokenEndpoint = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        try {
            // Resource Owner Password Grant
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "password");
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);
            formData.add("username", username);
            formData.add("password", password);
            formData.add("scope", "openid");

            Map<String, Object> tokenResponse = webClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                throw new AuthenticationException("Keycloak authentication failed: no access_token in response");
            }

            // Parse the access token JWT to extract claims and roles
            String accessToken = tokenResponse.get("access_token").toString();
            Map<String, Object> claims = parseJwtPayload(accessToken);

            String subject = getString(claims, "sub");
            String email = getString(claims, "email", username);
            String displayName = getString(claims, "name",
                    getString(claims, "preferred_username", username));

            // Extract roles from Keycloak JWT
            Set<String> roles = extractKeycloakRoles(claims, clientId);
            if (roles.isEmpty()) {
                roles.add(SecurityConstants.ROLE_DEVELOPER);
            }

            return AuthenticationResult.builder()
                    .externalId(subject)
                    .email(email)
                    .displayName(displayName)
                    .roles(roles)
                    .authProvider(SecurityConstants.AUTH_PROVIDER_KEYCLOAK)
                    .attributes(Map.of(
                            "subject", subject,
                            "keycloakAccessToken", accessToken,
                            "realm", realm
                    ))
                    .build();

        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Keycloak authentication failed for user: {}", username, e);
            throw new AuthenticationException("Keycloak authentication failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractKeycloakRoles(Map<String, Object> claims, String clientId) {
        Set<String> roles = new HashSet<>();

        // Extract realm roles from realm_access.roles
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map) {
            Map<String, Object> realmAccessMap = (Map<String, Object>) realmAccess;
            Object realmRoles = realmAccessMap.get("roles");
            if (realmRoles instanceof List) {
                for (Object role : (List<Object>) realmRoles) {
                    roles.add(role.toString());
                }
            }
        }

        // Extract client roles from resource_access.<clientId>.roles
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map) {
            Map<String, Object> resourceAccessMap = (Map<String, Object>) resourceAccess;
            Object clientAccess = resourceAccessMap.get(clientId);
            if (clientAccess instanceof Map) {
                Map<String, Object> clientAccessMap = (Map<String, Object>) clientAccess;
                Object clientRoles = clientAccessMap.get("roles");
                if (clientRoles instanceof List) {
                    for (Object role : (List<Object>) clientRoles) {
                        roles.add(role.toString());
                    }
                }
            }
        }

        return roles;
    }

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
            throw new AuthenticationException("Failed to parse Keycloak JWT: " + e.getMessage());
        }
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
