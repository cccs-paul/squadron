package com.squadron.identity.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityGroupRepositoryTest {

    @Test
    void should_extendJpaRepository_when_checked() {
        assertTrue(JpaRepository.class.isAssignableFrom(SecurityGroupRepository.class));
    }

    @Test
    void should_beInterface_when_checked() {
        assertTrue(SecurityGroupRepository.class.isInterface());
    }

    @Test
    void should_haveFindByTenantIdMethod_when_checked() throws NoSuchMethodException {
        Method method = SecurityGroupRepository.class.getDeclaredMethod(
                "findByTenantId", UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByTenantIdAndNameMethod_when_checked() throws NoSuchMethodException {
        Method method = SecurityGroupRepository.class.getDeclaredMethod(
                "findByTenantIdAndName", UUID.class, String.class);

        assertNotNull(method);
    }

    @Test
    void should_haveRepositoryAnnotation_when_checked() {
        assertTrue(SecurityGroupRepository.class.isAnnotationPresent(
                org.springframework.stereotype.Repository.class));
    }

    @Test
    void should_useCorrectGenericTypes_when_checked() {
        String genericInterface = Arrays.stream(SecurityGroupRepository.class.getGenericInterfaces())
                .map(Object::toString)
                .findFirst()
                .orElse("");
        assertTrue(genericInterface.contains("SecurityGroup"));
        assertTrue(genericInterface.contains("UUID"));
    }
}
