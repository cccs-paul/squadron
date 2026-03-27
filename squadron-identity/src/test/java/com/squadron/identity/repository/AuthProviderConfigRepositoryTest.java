package com.squadron.identity.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthProviderConfigRepositoryTest {

    @Test
    void should_extendJpaRepository_when_checked() {
        assertTrue(JpaRepository.class.isAssignableFrom(AuthProviderConfigRepository.class));
    }

    @Test
    void should_beInterface_when_checked() {
        assertTrue(AuthProviderConfigRepository.class.isInterface());
    }

    @Test
    void should_haveFindByTenantIdMethod_when_checked() throws NoSuchMethodException {
        Method method = AuthProviderConfigRepository.class.getDeclaredMethod(
                "findByTenantId", UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByTenantIdAndProviderTypeMethod_when_checked() throws NoSuchMethodException {
        Method method = AuthProviderConfigRepository.class.getDeclaredMethod(
                "findByTenantIdAndProviderType", UUID.class, String.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByTenantIdAndEnabledMethod_when_checked() throws NoSuchMethodException {
        Method method = AuthProviderConfigRepository.class.getDeclaredMethod(
                "findByTenantIdAndEnabled", UUID.class, boolean.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByTenantIdAndEnabledOrderByPriorityAscMethod_when_checked() throws NoSuchMethodException {
        Method method = AuthProviderConfigRepository.class.getDeclaredMethod(
                "findByTenantIdAndEnabledOrderByPriorityAsc", UUID.class, boolean.class);

        assertNotNull(method);
    }

    @Test
    void should_haveRepositoryAnnotation_when_checked() {
        assertTrue(AuthProviderConfigRepository.class.isAnnotationPresent(
                org.springframework.stereotype.Repository.class));
    }

    @Test
    void should_useCorrectGenericTypes_when_checked() {
        String genericInterface = Arrays.stream(AuthProviderConfigRepository.class.getGenericInterfaces())
                .map(Object::toString)
                .findFirst()
                .orElse("");
        assertTrue(genericInterface.contains("AuthProviderConfig"));
        assertTrue(genericInterface.contains("UUID"));
    }
}
