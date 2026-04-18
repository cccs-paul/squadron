package com.squadron.review.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewBotConfigRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(ReviewBotConfigRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(ReviewBotConfigRepository.class.isInterface());
    }

    @Test
    void should_declareFindByTenantIdAndConnectionId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewBotConfigRepository.class.getMethod(
                "findByTenantIdAndConnectionId", UUID.class, UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewBotConfigRepository.class.getMethod("findByTenantId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndConnectionIdAndEnabledTrue_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewBotConfigRepository.class.getMethod(
                "findByTenantIdAndConnectionIdAndEnabledTrue", UUID.class, UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_haveFourCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = ReviewBotConfigRepository.class.getDeclaredMethods();
        assertEquals(4, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = ReviewBotConfigRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("ReviewBotConfig"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
