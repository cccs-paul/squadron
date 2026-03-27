package com.squadron.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SecurityConfig.
 */
class SecurityConfigTest {

    @Test
    void should_beAnnotatedWithConfiguration() {
        var annotation = SecurityConfig.class
                .getAnnotation(org.springframework.context.annotation.Configuration.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void should_beAnnotatedWithEnableWebFluxSecurity() {
        var annotation = SecurityConfig.class.getAnnotation(EnableWebFluxSecurity.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void should_createJwtDecoder_when_squadronMode() throws Exception {
        SecurityConfig config = createSecurityConfig("squadron", "http://localhost:8081/api/auth/jwks", null);

        ReactiveJwtDecoder decoder = config.jwtDecoder();
        assertThat(decoder).isNotNull();
    }

    @Test
    void should_createJwtDecoder_when_keycloakMode() throws Exception {
        SecurityConfig config = createSecurityConfig("keycloak", null, "http://keycloak:8080/realms/squadron/protocol/openid-connect/certs");

        ReactiveJwtDecoder decoder = config.jwtDecoder();
        assertThat(decoder).isNotNull();
    }

    @Test
    void should_createJwtDecoder_when_hybridModeWithBothUris() throws Exception {
        SecurityConfig config = createSecurityConfig(
                "hybrid",
                "http://localhost:8081/api/auth/jwks",
                "http://keycloak:8080/realms/squadron/protocol/openid-connect/certs"
        );

        ReactiveJwtDecoder decoder = config.jwtDecoder();
        assertThat(decoder).isNotNull();
    }

    @Test
    void should_fallbackToDefault_when_squadronModeAndNoJwksUri() throws Exception {
        SecurityConfig config = createSecurityConfig("squadron", null, null);

        ReactiveJwtDecoder decoder = config.jwtDecoder();
        assertThat(decoder).isNotNull();
    }

    @Test
    void should_fallbackToKeycloakUri_when_squadronModeAndOnlyKeycloakAvailable() throws Exception {
        SecurityConfig config = createSecurityConfig("squadron", null, "http://keycloak:8080/certs");

        ReactiveJwtDecoder decoder = config.jwtDecoder();
        assertThat(decoder).isNotNull();
    }

    @Test
    void should_useNonHybridPath_when_hybridModeButOnlyOneUri() throws Exception {
        // If hybrid mode but only squadron JWKS URI is set
        SecurityConfig config = createSecurityConfig("hybrid", "http://localhost:8081/api/auth/jwks", null);

        ReactiveJwtDecoder decoder = config.jwtDecoder();
        assertThat(decoder).isNotNull();
    }

    @Test
    void should_haveSecurityWebFilterChainBeanMethod() throws NoSuchMethodException {
        var method = SecurityConfig.class.getMethod("securityWebFilterChain",
                org.springframework.security.config.web.server.ServerHttpSecurity.class);
        var beanAnnotation = method.getAnnotation(org.springframework.context.annotation.Bean.class);
        assertThat(beanAnnotation).isNotNull();
    }

    @Test
    void should_haveJwtDecoderBeanMethod() throws NoSuchMethodException {
        var method = SecurityConfig.class.getMethod("jwtDecoder");
        var beanAnnotation = method.getAnnotation(org.springframework.context.annotation.Bean.class);
        assertThat(beanAnnotation).isNotNull();
    }

    @Test
    void should_haveAuthModeField() throws NoSuchFieldException {
        Field field = SecurityConfig.class.getDeclaredField("authMode");
        assertThat(field).isNotNull();
        assertThat(field.getType()).isEqualTo(String.class);
    }

    private SecurityConfig createSecurityConfig(String authMode, String squadronJwksUri, String keycloakJwksUri) throws Exception {
        SecurityConfig config = new SecurityConfig();

        setField(config, "authMode", authMode);
        setField(config, "squadronJwksUri", squadronJwksUri);
        setField(config, "keycloakJwksUri", keycloakJwksUri);

        return config;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
