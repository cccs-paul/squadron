package com.squadron.agent.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(ConversationRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(ConversationRepository.class.isInterface());
    }

    @Test
    void should_declareFindByTaskIdAndAgentType_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ConversationRepository.class.getMethod(
                "findByTaskIdAndAgentType", UUID.class, String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByTaskId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ConversationRepository.class.getMethod("findByTaskId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareFindByUserIdAndStatus_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ConversationRepository.class.getMethod(
                "findByUserIdAndStatus", UUID.class, String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByIdAndTenantId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ConversationRepository.class.getMethod(
                "findByIdAndTenantId", UUID.class, UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndStatus_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ConversationRepository.class.getMethod(
                "findByTenantIdAndStatus", UUID.class, String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdOrderByUpdatedAtDesc_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ConversationRepository.class.getMethod(
                "findByTenantIdOrderByUpdatedAtDesc", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareCountByTenantId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ConversationRepository.class.getMethod("countByTenantId", UUID.class);
        assertEquals(long.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareCountByTenantIdAndStatus_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ConversationRepository.class.getMethod(
                "countByTenantIdAndStatus", UUID.class, String.class);
        assertEquals(long.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_haveEightCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = ConversationRepository.class.getDeclaredMethods();
        assertEquals(8, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = ConversationRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("Conversation"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
