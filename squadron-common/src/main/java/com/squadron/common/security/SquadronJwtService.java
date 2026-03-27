package com.squadron.common.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jwt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class SquadronJwtService {
    private static final Logger log = LoggerFactory.getLogger(SquadronJwtService.class);

    private final RSAKey rsaKey;
    private final RSASSASigner signer;
    private final RSASSAVerifier verifier;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    @Autowired
    public SquadronJwtService(
            @Value("${squadron.security.jwt.issuer:squadron}") String issuer,
            @Value("${squadron.security.jwt.access-token-ttl:PT1H}") Duration accessTokenTtl,
            @Value("${squadron.security.jwt.refresh-token-ttl:P7D}") Duration refreshTokenTtl,
            @Value("${squadron.security.jwt.rsa-key-json:#{null}}") String rsaKeyJson) throws Exception {
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;

        if (rsaKeyJson != null && !rsaKeyJson.isBlank()) {
            this.rsaKey = RSAKey.parse(rsaKeyJson);
        } else {
            // Generate ephemeral key for development - NOT for production
            this.rsaKey = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        }
        this.signer = new RSASSASigner(this.rsaKey);
        this.verifier = new RSASSAVerifier(this.rsaKey.toPublicJWK());
    }

    // Test constructor
    public SquadronJwtService(RSAKey rsaKey, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) throws Exception {
        this.rsaKey = rsaKey;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.signer = new RSASSASigner(this.rsaKey);
        this.verifier = new RSASSAVerifier(this.rsaKey.toPublicJWK());
    }

    public String generateAccessToken(UUID userId, UUID tenantId, String email,
                                       String displayName, Set<String> roles, String authProvider) {
        return generateToken(userId, tenantId, email, displayName, roles, authProvider,
                            SecurityConstants.TOKEN_TYPE_ACCESS, accessTokenTtl);
    }

    public String generateRefreshToken(UUID userId, UUID tenantId, String email,
                                        String displayName, Set<String> roles, String authProvider) {
        return generateToken(userId, tenantId, email, displayName, roles, authProvider,
                            SecurityConstants.TOKEN_TYPE_REFRESH, refreshTokenTtl);
    }

    private String generateToken(UUID userId, UUID tenantId, String email, String displayName,
                                  Set<String> roles, String authProvider, String tokenType, Duration ttl) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issuer(issuer)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(ttl)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim(SecurityConstants.CLAIM_TENANT_ID, tenantId.toString())
                    .claim(SecurityConstants.CLAIM_USER_ID, userId.toString())
                    .claim(SecurityConstants.CLAIM_EMAIL, email)
                    .claim(SecurityConstants.CLAIM_ROLES, new ArrayList<>(roles))
                    .claim(SecurityConstants.CLAIM_AUTH_PROVIDER, authProvider)
                    .claim("token_type", tokenType);

            if (displayName != null) {
                claimsBuilder.claim(SecurityConstants.CLAIM_DISPLAY_NAME, displayName);
            }

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    claimsBuilder.build());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new SecurityException("Failed to generate JWT", e);
        }
    }

    public JWTClaimsSet validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(verifier)) {
                throw new SecurityException("Invalid JWT signature");
            }
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime() != null && claims.getExpirationTime().before(new Date())) {
                throw new SecurityException("JWT has expired");
            }
            if (!issuer.equals(claims.getIssuer())) {
                throw new SecurityException("Invalid JWT issuer");
            }
            return claims;
        } catch (ParseException | JOSEException e) {
            throw new SecurityException("Failed to validate JWT", e);
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            return claims.getExpirationTime() != null && claims.getExpirationTime().before(new Date());
        } catch (ParseException e) {
            return true;
        }
    }

    public Map<String, Object> getPublicJwks() {
        JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());
        return jwkSet.toJSONObject();
    }

    public String getIssuer() {
        return issuer;
    }
}
