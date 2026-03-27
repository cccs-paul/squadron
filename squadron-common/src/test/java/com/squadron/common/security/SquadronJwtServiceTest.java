package com.squadron.common.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SquadronJwtServiceTest {

    private SquadronJwtService jwtService;
    private RSAKey rsaKey;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048)
                .keyID("test-key-id")
                .generate();
        jwtService = new SquadronJwtService(rsaKey, "squadron-test", Duration.ofHours(1), Duration.ofDays(7));
    }

    @Test
    void should_generateAccessToken_when_validInputs() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Set<String> roles = Set.of("developer", "qa");

        String token = jwtService.generateAccessToken(userId, tenantId, "test@example.com", "Test User", roles, "ldap");

        assertNotNull(token);
        assertFalse(token.isBlank());
        // JWT has 3 parts separated by dots
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void should_generateRefreshToken_when_validInputs() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Set<String> roles = Set.of("developer");

        String token = jwtService.generateRefreshToken(userId, tenantId, "test@example.com", "Test User", roles, "oidc");

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void should_validateToken_when_tokenIsValid() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Set<String> roles = Set.of("developer");

        String token = jwtService.generateAccessToken(userId, tenantId, "test@example.com", "Test User", roles, "ldap");

        JWTClaimsSet claims = jwtService.validateToken(token);

        assertNotNull(claims);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("squadron-test", claims.getIssuer());
        assertEquals(tenantId.toString(), claims.getStringClaim(SecurityConstants.CLAIM_TENANT_ID));
        assertEquals("test@example.com", claims.getStringClaim(SecurityConstants.CLAIM_EMAIL));
        assertEquals("ldap", claims.getStringClaim(SecurityConstants.CLAIM_AUTH_PROVIDER));
        assertEquals("Test User", claims.getStringClaim(SecurityConstants.CLAIM_DISPLAY_NAME));
        assertEquals("access", claims.getStringClaim("token_type"));
    }

    @Test
    void should_validateRefreshToken_when_tokenIsValid() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Set<String> roles = Set.of("developer");

        String token = jwtService.generateRefreshToken(userId, tenantId, "test@example.com", "Test User", roles, "keycloak");

        JWTClaimsSet claims = jwtService.validateToken(token);

        assertEquals("refresh", claims.getStringClaim("token_type"));
        assertEquals("keycloak", claims.getStringClaim(SecurityConstants.CLAIM_AUTH_PROVIDER));
    }

    @Test
    void should_throwSecurityException_when_tokenIsInvalid() {
        assertThrows(SecurityException.class, () -> jwtService.validateToken("invalid.token.here"));
    }

    @Test
    void should_throwSecurityException_when_tokenIsTampered() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, tenantId, "test@example.com", "Test", Set.of("dev"), "ldap");
        // Tamper with the signature by modifying the last character
        String tamperedToken = token.substring(0, token.length() - 2) + "XX";

        assertThrows(SecurityException.class, () -> jwtService.validateToken(tamperedToken));
    }

    @Test
    void should_throwSecurityException_when_issuerMismatch() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("other-key").generate();
        SquadronJwtService otherService = new SquadronJwtService(otherKey, "other-issuer", Duration.ofHours(1), Duration.ofDays(7));

        String token = otherService.generateAccessToken(UUID.randomUUID(), UUID.randomUUID(),
                "test@example.com", "Test", Set.of("dev"), "ldap");

        assertThrows(SecurityException.class, () -> jwtService.validateToken(token));
    }

    @Test
    void should_detectExpiredToken_when_tokenHasExpired() throws Exception {
        SquadronJwtService shortLivedService = new SquadronJwtService(rsaKey, "squadron-test",
                Duration.ofMillis(1), Duration.ofMillis(1));

        String token = shortLivedService.generateAccessToken(UUID.randomUUID(), UUID.randomUUID(),
                "test@example.com", "Test", Set.of("dev"), "ldap");

        Thread.sleep(50);
        assertTrue(jwtService.isTokenExpired(token));
    }

    @Test
    void should_returnFalse_when_tokenNotExpired() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), UUID.randomUUID(),
                "test@example.com", "Test", Set.of("dev"), "ldap");

        assertFalse(jwtService.isTokenExpired(token));
    }

    @Test
    void should_returnTrue_when_isTokenExpiredCalledWithInvalidToken() {
        assertTrue(jwtService.isTokenExpired("not-a-jwt"));
    }

    @Test
    void should_returnPublicJwks_when_called() {
        Map<String, Object> jwks = jwtService.getPublicJwks();

        assertNotNull(jwks);
        assertTrue(jwks.containsKey("keys"));
    }

    @Test
    void should_returnIssuer_when_called() {
        assertEquals("squadron-test", jwtService.getIssuer());
    }

    @Test
    void should_handleNullDisplayName_when_generatingToken() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, tenantId, "test@example.com", null, Set.of("dev"), "ldap");
        JWTClaimsSet claims = jwtService.validateToken(token);

        assertNull(claims.getStringClaim(SecurityConstants.CLAIM_DISPLAY_NAME));
    }

    @Test
    void should_includeRolesInClaims_when_multipleRolesProvided() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Set<String> roles = Set.of("developer", "qa", "squadron-admin");

        String token = jwtService.generateAccessToken(userId, tenantId, "test@example.com", "Test", roles, "ldap");
        JWTClaimsSet claims = jwtService.validateToken(token);

        var claimRoles = claims.getStringListClaim(SecurityConstants.CLAIM_ROLES);
        assertNotNull(claimRoles);
        assertEquals(3, claimRoles.size());
        assertTrue(claimRoles.containsAll(roles));
    }

    @Test
    void should_generateEphemeralKey_when_noRsaKeyJsonProvided() throws Exception {
        SquadronJwtService ephemeralService = new SquadronJwtService("squadron", Duration.ofHours(1), Duration.ofDays(7), null);

        String token = ephemeralService.generateAccessToken(UUID.randomUUID(), UUID.randomUUID(),
                "test@example.com", "Test", Set.of("dev"), "ldap");
        assertNotNull(token);

        JWTClaimsSet claims = ephemeralService.validateToken(token);
        assertNotNull(claims);
    }
}
