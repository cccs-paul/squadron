package com.squadron.identity.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePermissionRepositoryTest {

    @Test
    void should_extendJpaRepository_when_checked() {
        assertTrue(JpaRepository.class.isAssignableFrom(ResourcePermissionRepository.class));
    }

    @Test
    void should_beInterface_when_checked() {
        assertTrue(ResourcePermissionRepository.class.isInterface());
    }

    @Test
    void should_haveFindByTenantIdAndResourceTypeAndResourceIdMethod_when_checked() throws NoSuchMethodException {
        Method method = ResourcePermissionRepository.class.getDeclaredMethod(
                "findByTenantIdAndResourceTypeAndResourceId", UUID.class, String.class, UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByGranteeTypeAndGranteeIdMethod_when_checked() throws NoSuchMethodException {
        Method method = ResourcePermissionRepository.class.getDeclaredMethod(
                "findByGranteeTypeAndGranteeId", String.class, UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByTenantIdAndGranteeTypeAndGranteeIdMethod_when_checked() throws NoSuchMethodException {
        Method method = ResourcePermissionRepository.class.getDeclaredMethod(
                "findByTenantIdAndGranteeTypeAndGranteeId", UUID.class, String.class, UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveRepositoryAnnotation_when_checked() {
        assertTrue(ResourcePermissionRepository.class.isAnnotationPresent(
                org.springframework.stereotype.Repository.class));
    }

    @Test
    void should_useCorrectGenericTypes_when_checked() {
        String genericInterface = Arrays.stream(ResourcePermissionRepository.class.getGenericInterfaces())
                .map(Object::toString)
                .findFirst()
                .orElse("");
        assertTrue(genericInterface.contains("ResourcePermission"));
        assertTrue(genericInterface.contains("UUID"));
    }
}
