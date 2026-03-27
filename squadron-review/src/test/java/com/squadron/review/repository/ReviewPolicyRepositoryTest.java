package com.squadron.review.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewPolicyRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(ReviewPolicyRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(ReviewPolicyRepository.class.isInterface());
    }

    @Test
    void should_declareFindByTenantIdAndTeamId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewPolicyRepository.class.getMethod(
                "findByTenantIdAndTeamId", UUID.class, UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndTeamIdIsNull_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewPolicyRepository.class.getMethod(
                "findByTenantIdAndTeamIdIsNull", UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_haveTwoCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = ReviewPolicyRepository.class.getDeclaredMethods();
        assertEquals(2, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = ReviewPolicyRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("ReviewPolicy"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
