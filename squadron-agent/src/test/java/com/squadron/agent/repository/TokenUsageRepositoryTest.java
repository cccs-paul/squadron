package com.squadron.agent.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TokenUsageRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(TokenUsageRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(TokenUsageRepository.class.isInterface());
    }

    @Test
    void should_declareFindByTenantId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = TokenUsageRepository.class.getMethod("findByTenantId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndUserId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = TokenUsageRepository.class.getMethod("findByTenantIdAndUserId", UUID.class, UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndTeamId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = TokenUsageRepository.class.getMethod("findByTenantIdAndTeamId", UUID.class, UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndAgentType_when_interfaceInspected() throws NoSuchMethodException {
        Method method = TokenUsageRepository.class.getMethod("findByTenantIdAndAgentType", UUID.class, String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndCreatedAtBetween_when_interfaceInspected() throws NoSuchMethodException {
        Method method = TokenUsageRepository.class.getMethod(
                "findByTenantIdAndCreatedAtBetween", UUID.class, Instant.class, Instant.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(3, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndUserIdAndCreatedAtBetween_when_interfaceInspected() throws NoSuchMethodException {
        Method method = TokenUsageRepository.class.getMethod(
                "findByTenantIdAndUserIdAndCreatedAtBetween", UUID.class, UUID.class, Instant.class, Instant.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(4, method.getParameterCount());
    }

    @Test
    void should_haveSixCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = TokenUsageRepository.class.getDeclaredMethods();
        assertEquals(6, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = TokenUsageRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("TokenUsage"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
