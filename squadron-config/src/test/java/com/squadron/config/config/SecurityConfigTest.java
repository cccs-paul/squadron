package com.squadron.config.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecurityConfigTest {

    @Test
    void should_createJwtAuthenticationConverter() {
        SecurityConfig securityConfig = new SecurityConfig();

        JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();

        assertNotNull(converter);
    }
}
