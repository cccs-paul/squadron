package com.squadron.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.repository.PlatformConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;

@Service
public class GitHubAppTokenService {

    private static final Logger log = LoggerFactory.getLogger(GitHubAppTokenService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final PlatformConnectionRepository connectionRepository;
    private final TokenEncryptionService encryptionService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /** Cache of installation tokens: installationId -> CachedToken */
    private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    record CachedToken(String token, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt.minusSeconds(60));
        }
    }

    public GitHubAppTokenService(PlatformConnectionRepository connectionRepository,
                                  TokenEncryptionService encryptionService,
                                  WebClient.Builder webClientBuilder,
                                  ObjectMapper objectMapper) {
        this.connectionRepository = connectionRepository;
        this.encryptionService = encryptionService;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * Get or generate an installation access token for a GitHub App connection.
     * Tokens are cached until near expiry (1-hour TTL from GitHub, cached for ~55 min).
     *
     * @param connectionId the platform connection configured as a GitHub App
     * @return the installation access token
     * @throws IllegalStateException if the connection is not a GitHub App
     */
    public String getInstallationToken(UUID connectionId) {
        PlatformConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalStateException("Connection not found: " + connectionId));

        if (!"GITHUB_APP".equalsIgnoreCase(connection.getAuthType())) {
            throw new IllegalStateException("Connection " + connectionId + " is not a GitHub App (authType=" + connection.getAuthType() + ")");
        }

        Map<String, Object> creds = parseCredentials(connection);
        String installationId = String.valueOf(creds.get("installationId"));
        String cacheKey = connectionId + ":" + installationId;

        // Check cache
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.token();
        }

        // Generate new token
        String appJwt = generateAppJwt(creds);
        String token = exchangeForInstallationToken(appJwt, installationId);

        // Cache for ~55 minutes (GitHub tokens expire after 1 hour)
        tokenCache.put(cacheKey, new CachedToken(token, Instant.now().plusSeconds(3300)));

        return token;
    }

    /**
     * Check if a connection is configured as a GitHub App.
     */
    public boolean isGitHubApp(PlatformConnection connection) {
        return "GITHUB_APP".equalsIgnoreCase(connection.getAuthType())
                && "GITHUB".equalsIgnoreCase(connection.getPlatformType());
    }

    /**
     * Generate a JWT signed with the GitHub App's private key.
     * The JWT is used to authenticate as the GitHub App for 10 minutes.
     */
    String generateAppJwt(Map<String, Object> creds) {
        try {
            Object appIdObj = creds.get("appId");
            String appId = String.valueOf(appIdObj);
            String privateKeyPem = (String) creds.get("privateKeyPem");

            // Decrypt if encrypted
            if (encryptionService.isEncrypted(privateKeyPem)) {
                privateKeyPem = encryptionService.decrypt(privateKeyPem);
            }

            RSAPrivateKey rsaKey = parseRSAPrivateKey(privateKeyPem);

            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(appId)
                    .issueTime(java.util.Date.from(now.minusSeconds(60)))
                    .expirationTime(java.util.Date.from(now.plusSeconds(600)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claims);
            signedJWT.sign(new RSASSASigner(rsaKey));

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate GitHub App JWT", e);
        }
    }

    /**
     * Exchange an App JWT for an installation access token.
     */
    String exchangeForInstallationToken(String appJwt, String installationId) {
        try {
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(GITHUB_API_BASE + "/app/installations/" + installationId + "/access_tokens")
                    .header("Authorization", "Bearer " + appJwt)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null || !response.containsKey("token")) {
                throw new IllegalStateException("GitHub App installation token exchange returned no token");
            }

            return (String) response.get("token");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get GitHub App installation token for installation " + installationId, e);
        }
    }

    /**
     * Parse a PEM-encoded RSA private key.
     */
    RSAPrivateKey parseRSAPrivateKey(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(spec);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseCredentials(PlatformConnection connection) {
        try {
            return objectMapper.readValue(connection.getCredentials(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse credentials for connection " + connection.getId(), e);
        }
    }

    /**
     * Visible for testing: clear the token cache.
     */
    void clearCache() {
        tokenCache.clear();
    }
}
