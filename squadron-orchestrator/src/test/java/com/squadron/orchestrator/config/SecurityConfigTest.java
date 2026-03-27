package com.squadron.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void should_beAnnotatedWithConfiguration() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(Configuration.class));
    }

    @Test
    void should_beAnnotatedWithEnableWebSecurity() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(EnableWebSecurity.class));
    }

    @Test
    void should_beAnnotatedWithEnableMethodSecurity() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class));
    }

    @Test
    void should_haveSecurityFilterChainMethod() throws NoSuchMethodException {
        var method = SecurityConfig.class.getMethod("securityFilterChain",
                org.springframework.security.config.annotation.web.builders.HttpSecurity.class);
        assertNotNull(method);
    }

    @Test
    void should_haveJwtDecoderMethod() throws NoSuchMethodException {
        var method = SecurityConfig.class.getMethod("jwtDecoder");
        assertNotNull(method);
    }

    @Test
    void should_haveJwtAuthenticationConverterMethod() throws NoSuchMethodException {
        var method = SecurityConfig.class.getMethod("jwtAuthenticationConverter");
        assertNotNull(method);
        assertTrue(JwtAuthenticationConverter.class.isAssignableFrom(method.getReturnType()));
    }
}
