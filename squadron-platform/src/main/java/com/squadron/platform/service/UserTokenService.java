package com.squadron.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.dto.ConnectionInfoResponse;
import com.squadron.platform.dto.LinkAccountRequest;
import com.squadron.platform.dto.OAuth2AuthorizeUrlResponse;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.entity.UserPlatformToken;
import com.squadron.platform.repository.PlatformConnectionRepository;
import com.squadron.platform.repository.UserPlatformTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserTokenService {

    private static final Logger log = LoggerFactory.getLogger(UserTokenService.class);
    private static final Duration STATE_EXPIRATION = Duration.ofMinutes(10);

    /**
     * In-memory store for OAuth2 CSRF state tokens.
     * Maps state string -> StateEntry (connectionId + creation time).
     */
    private final ConcurrentHashMap<String, StateEntry> stateStore = new ConcurrentHashMap<>();

    private record StateEntry(UUID connectionId, Instant createdAt) {}

    private final UserPlatformTokenRepository tokenRepository;
    private final PlatformConnectionRepository connectionRepository;
    private final TokenEncryptionService encryptionService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public UserTokenService(UserPlatformTokenRepository tokenRepository,
                            PlatformConnectionRepository connectionRepository,
                            TokenEncryptionService encryptionService,
                            WebClient.Builder webClientBuilder,
                            ObjectMapper objectMapper) {
        this.tokenRepository = tokenRepository;
        this.connectionRepository = connectionRepository;
        this.encryptionService = encryptionService;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * Link a user's platform account via OAuth2 authorization code exchange.
     * Exchanges the auth code for access/refresh tokens, encrypts them, and stores them.
     */
    public UserPlatformToken linkOAuth2Account(UUID userId, UUID connectionId,
                                                String authorizationCode, String redirectUri) {
        log.info("Linking OAuth2 account for user {} on connection {}", userId, connectionId);

        PlatformConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", connectionId));

        // Get OAuth2 token endpoint from connection credentials/metadata
        String tokenEndpoint = extractFromConfig(connection, "tokenEndpoint");
        String clientId = extractFromConfig(connection, "clientId");
        String clientSecret = encryptionService.decrypt(extractFromConfig(connection, "clientSecret"));

        // Exchange authorization code for tokens
        Map<String, Object> tokenResponse = webClientBuilder.build()
                .post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                        .with("code", authorizationCode)
                        .with("redirect_uri", redirectUri)
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (tokenResponse == null) {
            throw new SecurityException("OAuth2 token exchange returned null response");
        }

        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");
        Integer expiresIn = tokenResponse.get("expires_in") instanceof Number n ? n.intValue() : null;

        if (accessToken == null) {
            throw new SecurityException("OAuth2 token exchange did not return an access_token");
        }

        // Build token metadata from additional OAuth2 response fields
        String tokenMetadata = buildTokenMetadata(tokenResponse);

        // Create or update the token record with encrypted tokens
        UserPlatformToken token = tokenRepository.findByUserIdAndConnectionId(userId, connectionId)
                .orElse(new UserPlatformToken());
        token.setUserId(userId);
        token.setConnectionId(connectionId);
        token.setAccessToken(encryptionService.encrypt(accessToken));
        token.setRefreshToken(refreshToken != null ? encryptionService.encrypt(refreshToken) : null);
        token.setExpiresAt(expiresIn != null ? Instant.now().plusSeconds(expiresIn) : null);
        token.setTokenType("oauth2");
        token.setScopes((String) tokenResponse.get("scope"));
        token.setTokenMetadata(tokenMetadata);

        UserPlatformToken saved = tokenRepository.save(token);
        log.info("Linked OAuth2 account for user {} on connection {}", userId, connectionId);
        return saved;
    }

    /**
     * Link a user's platform account via personal access token (PAT).
     * Encrypts the PAT and stores it.
     */
    public UserPlatformToken linkPatAccount(UUID userId, UUID connectionId, String accessToken) {
        log.info("Linking PAT account for user {} on connection {}", userId, connectionId);

        connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", connectionId));

        UserPlatformToken token = tokenRepository.findByUserIdAndConnectionId(userId, connectionId)
                .orElse(new UserPlatformToken());
        token.setUserId(userId);
        token.setConnectionId(connectionId);
        token.setAccessToken(encryptionService.encrypt(accessToken));
        token.setRefreshToken(null);
        token.setExpiresAt(null); // PATs don't expire via OAuth2
        token.setTokenType("pat");
        token.setTokenMetadata(null);

        UserPlatformToken saved = tokenRepository.save(token);
        log.info("Linked PAT account for user {} on connection {}", userId, connectionId);
        return saved;
    }

    /**
     * Links a user's platform account by storing their access token (legacy method).
     * Uses the authorization code exchange flow for OAuth2 connections.
     */
    public UserPlatformToken linkAccount(UUID userId, LinkAccountRequest request) {
        return linkOAuth2Account(userId, request.getConnectionId(),
                request.getAuthorizationCode(), request.getRedirectUri());
    }

    /**
     * Get decrypted access token for a user's platform connection.
     * Automatically refreshes expired OAuth2 tokens.
     */
    public String getDecryptedAccessToken(UUID userId, UUID connectionId) {
        UserPlatformToken token = tokenRepository.findByUserIdAndConnectionId(userId, connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("UserPlatformToken",
                        "userId=" + userId + ", connectionId=" + connectionId));

        // Check if OAuth2 token needs refresh
        if ("oauth2".equals(token.getTokenType()) && isExpired(token)) {
            token = refreshOAuth2Token(token);
        }

        return encryptionService.decrypt(token.getAccessToken());
    }

    /**
     * Retrieves the user's token for a given connection. Refreshes if expired.
     */
    @Transactional(readOnly = true)
    public UserPlatformToken getToken(UUID userId, UUID connectionId) {
        UserPlatformToken token = tokenRepository.findByUserIdAndConnectionId(userId, connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("UserPlatformToken",
                        "userId=" + userId + ", connectionId=" + connectionId));
        return token;
    }

    /**
     * Retrieves all platform tokens for a user.
     */
    @Transactional(readOnly = true)
    public List<UserPlatformToken> getTokensByUser(UUID userId) {
        return tokenRepository.findByUserId(userId);
    }

    /**
     * Unlinks a user's platform account by removing their stored token.
     */
    public void unlinkAccount(UUID userId, UUID connectionId) {
        UserPlatformToken token = tokenRepository.findByUserIdAndConnectionId(userId, connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("UserPlatformToken",
                        "userId=" + userId + ", connectionId=" + connectionId));
        tokenRepository.delete(token);
        log.info("Unlinked platform account for user {} on connection {}", userId, connectionId);
    }

    /**
     * Generates an OAuth2 authorization URL for the given platform connection.
     * Creates a random state parameter for CSRF protection and stores it for later validation.
     *
     * @param connectionId the platform connection to generate an authorize URL for
     * @return OAuth2AuthorizeUrlResponse containing the authorize URL, state, connectionId, and platformType
     * @throws ResourceNotFoundException if the connection does not exist
     * @throws SecurityException if the connection is missing required OAuth2 configuration
     */
    @Transactional(readOnly = true)
    public OAuth2AuthorizeUrlResponse generateOAuth2AuthorizeUrl(UUID connectionId) {
        log.info("Generating OAuth2 authorize URL for connection {}", connectionId);

        PlatformConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", connectionId));

        String clientId;
        String authorizeEndpoint;
        String scopes;
        String redirectUri;

        try {
            clientId = extractFromConfig(connection, "clientId");
        } catch (IllegalStateException e) {
            throw new SecurityException("Missing clientId for connection " + connectionId);
        }

        try {
            authorizeEndpoint = extractFromConfig(connection, "authorizeEndpoint");
        } catch (IllegalStateException e) {
            throw new SecurityException("Missing authorizeEndpoint for connection " + connectionId);
        }

        String scopeValue;
        try {
            scopeValue = extractFromConfig(connection, "scopes");
        } catch (IllegalStateException e) {
            scopeValue = "";
        }
        scopes = scopeValue;

        String redirectUriValue;
        try {
            redirectUriValue = extractFromConfig(connection, "redirectUri");
        } catch (IllegalStateException e) {
            redirectUriValue = "";
        }
        redirectUri = redirectUriValue;

        // Generate random state token for CSRF protection
        String state = UUID.randomUUID().toString();

        // Store state for later validation
        stateStore.put(state, new StateEntry(connectionId, Instant.now()));

        // Build the authorization URL with proper encoding
        String authorizeUrl = UriComponentsBuilder.fromUriString(authorizeEndpoint)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .queryParam("scope", scopes)
                .encode()
                .build()
                .toUriString();

        return OAuth2AuthorizeUrlResponse.builder()
                .authorizeUrl(authorizeUrl)
                .state(state)
                .connectionId(connectionId)
                .platformType(connection.getPlatformType())
                .build();
    }

    /**
     * Links an OAuth2 account with state validation for CSRF protection.
     * Validates the state parameter against the stored state before proceeding with the code exchange.
     *
     * @param userId the user to link
     * @param connectionId the platform connection
     * @param authorizationCode the authorization code from the OAuth2 callback
     * @param redirectUri the redirect URI used in the original authorize request
     * @param state the state parameter from the OAuth2 callback
     * @return the linked UserPlatformToken
     * @throws SecurityException if the state is invalid, expired, or does not match the connection
     */
    public UserPlatformToken linkOAuth2AccountWithState(UUID userId, UUID connectionId,
                                                         String authorizationCode, String redirectUri,
                                                         String state) {
        log.info("Linking OAuth2 account with state validation for user {} on connection {}", userId, connectionId);

        // Validate and consume the state token (one-time use)
        StateEntry stateEntry = stateStore.remove(state);
        if (stateEntry == null) {
            throw new SecurityException("Invalid OAuth2 state parameter");
        }

        // Check if state has expired (10 minute window)
        if (Duration.between(stateEntry.createdAt(), Instant.now()).compareTo(STATE_EXPIRATION) > 0) {
            throw new SecurityException("OAuth2 state parameter has expired");
        }

        // Verify the state was issued for this connection
        if (!stateEntry.connectionId().equals(connectionId)) {
            throw new SecurityException("OAuth2 state parameter does not match the connection");
        }

        // Delegate to the existing OAuth2 code exchange flow
        return linkOAuth2Account(userId, connectionId, authorizationCode, redirectUri);
    }

    /**
     * Lists all ACTIVE platform connections available for a tenant.
     * Returns safe DTOs that do not expose credentials.
     *
     * @param tenantId the tenant ID
     * @return list of ConnectionInfoResponse DTOs for active connections
     */
    @Transactional(readOnly = true)
    public List<ConnectionInfoResponse> getAvailableConnections(UUID tenantId) {
        log.info("Listing available connections for tenant {}", tenantId);
        List<PlatformConnection> connections = connectionRepository.findByTenantIdAndStatus(tenantId, "ACTIVE");
        return connections.stream()
                .map(ConnectionInfoResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Refresh an expired OAuth2 token using the stored refresh token.
     */
    private UserPlatformToken refreshOAuth2Token(UserPlatformToken token) {
        if (token.getRefreshToken() == null) {
            throw new SecurityException("Cannot refresh token: no refresh token available");
        }

        log.info("Refreshing OAuth2 token for user {} on connection {}", token.getUserId(), token.getConnectionId());

        PlatformConnection connection = connectionRepository.findById(token.getConnectionId())
                .orElseThrow(() -> new ResourceNotFoundException("PlatformConnection", token.getConnectionId()));

        String tokenEndpoint = extractFromConfig(connection, "tokenEndpoint");
        String clientId = extractFromConfig(connection, "clientId");
        String clientSecret = encryptionService.decrypt(extractFromConfig(connection, "clientSecret"));
        String decryptedRefreshToken = encryptionService.decrypt(token.getRefreshToken());

        Map<String, Object> tokenResponse = webClientBuilder.build()
                .post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                        .with("refresh_token", decryptedRefreshToken)
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (tokenResponse == null) {
            throw new SecurityException("OAuth2 token refresh returned null response");
        }

        String newAccessToken = (String) tokenResponse.get("access_token");
        if (newAccessToken == null) {
            throw new SecurityException("OAuth2 token refresh did not return an access_token");
        }

        token.setAccessToken(encryptionService.encrypt(newAccessToken));

        String newRefreshToken = (String) tokenResponse.get("refresh_token");
        if (newRefreshToken != null) {
            token.setRefreshToken(encryptionService.encrypt(newRefreshToken));
        }

        Integer expiresIn = tokenResponse.get("expires_in") instanceof Number n ? n.intValue() : null;
        if (expiresIn != null) {
            token.setExpiresAt(Instant.now().plusSeconds(expiresIn));
        }

        // Update token metadata with refresh response
        token.setTokenMetadata(buildTokenMetadata(tokenResponse));

        UserPlatformToken saved = tokenRepository.save(token);
        log.info("Refreshed OAuth2 token for user {} on connection {}", token.getUserId(), token.getConnectionId());
        return saved;
    }

    /**
     * Check if a token is expired or about to expire (within 60 seconds).
     */
    private boolean isExpired(UserPlatformToken token) {
        return token.getExpiresAt() != null && Instant.now().isAfter(token.getExpiresAt().minusSeconds(60));
    }

    /**
     * Extract a configuration value from the connection's credentials JSONB.
     */
    @SuppressWarnings("unchecked")
    private String extractFromConfig(PlatformConnection connection, String key) {
        if (connection.getCredentials() == null) {
            throw new IllegalStateException("Connection " + connection.getId() + " has no credentials configured");
        }
        try {
            Map<String, Object> creds = objectMapper.readValue(connection.getCredentials(), Map.class);
            Object value = creds.get(key);
            if (value == null) {
                // Also check metadata as a fallback
                if (connection.getMetadata() != null) {
                    Map<String, Object> meta = objectMapper.readValue(connection.getMetadata(), Map.class);
                    value = meta.get(key);
                }
            }
            if (value == null) {
                throw new IllegalStateException("Missing config key '" + key + "' for connection " + connection.getId());
            }
            return value.toString();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse credentials for connection " + connection.getId(), e);
        }
    }

    /**
     * Build token metadata JSON string from OAuth2 token response fields.
     * Captures additional response fields like token_type and scope for reference.
     */
    private String buildTokenMetadata(Map<String, Object> tokenResponse) {
        try {
            Map<String, Object> metadata = new java.util.HashMap<>();
            if (tokenResponse.containsKey("token_type")) {
                metadata.put("token_type", tokenResponse.get("token_type"));
            }
            if (tokenResponse.containsKey("scope")) {
                metadata.put("scope", tokenResponse.get("scope"));
            }
            if (tokenResponse.containsKey("id_token")) {
                metadata.put("has_id_token", true);
            }
            return metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize token metadata", e);
            return null;
        }
    }
}
