package com.squadron.git.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void should_createJwtAuthenticationConverter_bean() {
        JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();
        assertNotNull(converter);
    }

    @Test
    void should_haveSecurityFilterChainMethod() throws NoSuchMethodException {
        assertNotNull(SecurityConfig.class.getMethod("securityFilterChain",
                org.springframework.security.config.annotation.web.builders.HttpSecurity.class));
    }

    @Test
    void should_haveJwtDecoderMethod() throws NoSuchMethodException {
        assertNotNull(SecurityConfig.class.getMethod("jwtDecoder"));
    }

    @Test
    void should_haveJwtAuthenticationConverterMethod() throws NoSuchMethodException {
        assertNotNull(SecurityConfig.class.getMethod("jwtAuthenticationConverter"));
    }

    @Test
    void should_beAnnotatedWithConfiguration() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void should_beAnnotatedWithEnableWebSecurity() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(
                org.springframework.security.config.annotation.web.configuration.EnableWebSecurity.class));
    }

    @Test
    void should_beAnnotatedWithEnableMethodSecurity() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(
                org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity.class));
    }
}
