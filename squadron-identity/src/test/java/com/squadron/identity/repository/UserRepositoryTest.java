package com.squadron.identity.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest {

    @Test
    void should_extendJpaRepository_when_checked() {
        assertTrue(JpaRepository.class.isAssignableFrom(UserRepository.class));
    }

    @Test
    void should_beInterface_when_checked() {
        assertTrue(UserRepository.class.isInterface());
    }

    @Test
    void should_haveFindByExternalIdMethod_when_checked() throws NoSuchMethodException {
        Method method = UserRepository.class.getDeclaredMethod("findByExternalId", String.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByExternalIdAndTenantIdMethod_when_checked() throws NoSuchMethodException {
        Method method = UserRepository.class.getDeclaredMethod(
                "findByExternalIdAndTenantId", String.class, UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByEmailAndTenantIdMethod_when_checked() throws NoSuchMethodException {
        Method method = UserRepository.class.getDeclaredMethod(
                "findByEmailAndTenantId", String.class, UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByEmailMethod_when_checked() throws NoSuchMethodException {
        Method method = UserRepository.class.getDeclaredMethod("findByEmail", String.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByTenantIdMethod_when_checked() throws NoSuchMethodException {
        Method method = UserRepository.class.getDeclaredMethod("findByTenantId", UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByTenantIdAndRoleMethod_when_checked() throws NoSuchMethodException {
        Method method = UserRepository.class.getDeclaredMethod(
                "findByTenantIdAndRole", UUID.class, String.class);

        assertNotNull(method);
    }

    @Test
    void should_haveRepositoryAnnotation_when_checked() {
        assertTrue(UserRepository.class.isAnnotationPresent(
                org.springframework.stereotype.Repository.class));
    }

    @Test
    void should_useCorrectGenericTypes_when_checked() {
        String genericInterface = Arrays.stream(UserRepository.class.getGenericInterfaces())
                .map(Object::toString)
                .findFirst()
                .orElse("");
        assertTrue(genericInterface.contains("User"));
        assertTrue(genericInterface.contains("UUID"));
    }
}
