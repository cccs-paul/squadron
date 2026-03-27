package com.squadron.agent.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMessageRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(ConversationMessageRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(ConversationMessageRepository.class.isInterface());
    }

    @Test
    void should_declareFindByConversationIdOrderByCreatedAtAsc_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ConversationMessageRepository.class.getMethod(
                "findByConversationIdOrderByCreatedAtAsc", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareCountByConversationId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ConversationMessageRepository.class.getMethod(
                "countByConversationId", UUID.class);
        assertEquals(long.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_haveTwoCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = ConversationMessageRepository.class.getDeclaredMethods();
        assertEquals(2, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = ConversationMessageRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("ConversationMessage"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
