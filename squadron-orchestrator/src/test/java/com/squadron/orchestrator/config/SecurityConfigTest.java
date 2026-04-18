package com.squadron.orchestrator.config;

import com.squadron.common.security.BaseSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

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
    void should_extendBaseSecurityConfig() {
        assertTrue(BaseSecurityConfig.class.isAssignableFrom(SecurityConfig.class));
    }
}
