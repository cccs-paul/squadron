package com.squadron.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class BaseSecurityConfigTest {

    @Test
    void should_beAbstract() {
        assertTrue(Modifier.isAbstract(BaseSecurityConfig.class.getModifiers()));
    }

    @Test
    void should_haveSecurityFilterChainMethod() throws NoSuchMethodException {
        Method method = BaseSecurityConfig.class.getMethod("securityFilterChain", HttpSecurity.class);
        assertNotNull(method);
    }

    @Test
    void should_haveJwtDecoderMethod() throws NoSuchMethodException {
        Method method = BaseSecurityConfig.class.getMethod("jwtDecoder");
        assertNotNull(method);
        assertEquals(JwtDecoder.class, method.getReturnType());
    }

    @Test
    void should_haveJwtAuthenticationConverterMethod() throws NoSuchMethodException {
        Method method = BaseSecurityConfig.class.getMethod("jwtAuthenticationConverter");
        assertNotNull(method);
        assertEquals(JwtAuthenticationConverter.class, method.getReturnType());
    }

    @Test
    void should_haveProtectedAdditionalPermitAllPathsMethod() throws NoSuchMethodException {
        Method method = BaseSecurityConfig.class.getDeclaredMethod("additionalPermitAllPaths");
        assertNotNull(method);
        assertTrue(Modifier.isProtected(method.getModifiers()));
    }

    @Test
    void should_returnEmptyArrayByDefault() throws Exception {
        // Create a concrete subclass for testing
        BaseSecurityConfig config = new BaseSecurityConfig() {};
        Method method = BaseSecurityConfig.class.getDeclaredMethod("additionalPermitAllPaths");
        method.setAccessible(true);
        String[] paths = (String[]) method.invoke(config);
        assertNotNull(paths);
        assertEquals(0, paths.length);
    }
}
