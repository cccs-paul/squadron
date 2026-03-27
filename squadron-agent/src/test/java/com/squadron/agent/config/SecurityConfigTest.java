package com.squadron.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void should_extractRoles_fromRolesClaim() {
        SecurityConfig config = new SecurityConfig();
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("roles", List.of("squadron-admin", "developer"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_squadron-admin", "ROLE_developer");
    }

    @Test
    void should_extractRoles_fromKeycloakRealmAccess() {
        SecurityConfig config = new SecurityConfig();
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("realm_access", Map.of("roles", List.of("team-lead", "qa")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_team-lead", "ROLE_qa");
    }

    @Test
    void should_extractRoles_fromBothClaims() {
        SecurityConfig config = new SecurityConfig();
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("roles", List.of("developer"))
                .claim("realm_access", Map.of("roles", List.of("team-lead")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_developer", "ROLE_team-lead");
    }

    @Test
    void should_returnEmptyAuthorities_whenNoClaims() {
        SecurityConfig config = new SecurityConfig();
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities).isEmpty();
    }

    @Test
    void should_handleEmptyRolesList() {
        SecurityConfig config = new SecurityConfig();
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("roles", List.of())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities).isEmpty();
    }

    @Test
    void should_handleRealmAccessWithNoRolesKey() {
        SecurityConfig config = new SecurityConfig();
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("realm_access", Map.of("other", "value"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

        assertThat(authorities).isEmpty();
    }

    @Test
    void should_createJwtAuthenticationConverter() {
        SecurityConfig config = new SecurityConfig();
        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();

        assertThat(converter).isNotNull();
    }
}
