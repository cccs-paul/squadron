package com.squadron.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    @Test
    void should_haveConfigurationAnnotation_when_checked() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(Configuration.class));
    }

    @Test
    void should_haveEnableWebSecurityAnnotation_when_checked() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(EnableWebSecurity.class));
    }

    @Test
    void should_haveEnableMethodSecurityAnnotation_when_checked() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class));
    }

    @Test
    void should_haveSecurityFilterChainMethod_when_checked() throws NoSuchMethodException {
        Method method = SecurityConfig.class.getDeclaredMethod(
                "securityFilterChain",
                org.springframework.security.config.annotation.web.builders.HttpSecurity.class);

        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(org.springframework.context.annotation.Bean.class));
    }

    @Test
    void should_returnSecurityFilterChainType_when_checked() throws NoSuchMethodException {
        Method method = SecurityConfig.class.getDeclaredMethod(
                "securityFilterChain",
                org.springframework.security.config.annotation.web.builders.HttpSecurity.class);

        assertEquals(org.springframework.security.web.SecurityFilterChain.class, method.getReturnType());
    }

    @Test
    void should_haveJwtDecoderMethod_when_checked() throws NoSuchMethodException {
        Method method = SecurityConfig.class.getDeclaredMethod("jwtDecoder");

        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(org.springframework.context.annotation.Bean.class));
        assertEquals(JwtDecoder.class, method.getReturnType());
    }

    @Test
    void should_haveJwtAuthenticationConverterMethod_when_checked() throws NoSuchMethodException {
        Method method = SecurityConfig.class.getDeclaredMethod("jwtAuthenticationConverter");

        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(org.springframework.context.annotation.Bean.class));
        assertEquals(JwtAuthenticationConverter.class, method.getReturnType());
    }

    @Test
    void should_instantiateSecurityConfig_when_constructed() {
        SecurityConfig config = new SecurityConfig();
        assertNotNull(config);
    }
}
