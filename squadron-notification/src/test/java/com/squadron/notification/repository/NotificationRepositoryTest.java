package com.squadron.notification.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(NotificationRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(NotificationRepository.class.isInterface());
    }

    @Test
    void should_declareFindByUserIdAndStatusOrderByCreatedAtDesc_when_interfaceInspected() throws NoSuchMethodException {
        Method method = NotificationRepository.class.getMethod(
                "findByUserIdAndStatusOrderByCreatedAtDesc", UUID.class, String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByUserIdOrderByCreatedAtDesc_when_interfaceInspected() throws NoSuchMethodException {
        Method method = NotificationRepository.class.getMethod(
                "findByUserIdOrderByCreatedAtDesc", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareCountByUserIdAndStatus_when_interfaceInspected() throws NoSuchMethodException {
        Method method = NotificationRepository.class.getMethod(
                "countByUserIdAndStatus", UUID.class, String.class);
        assertEquals(long.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByStatus_when_interfaceInspected() throws NoSuchMethodException {
        Method method = NotificationRepository.class.getMethod("findByStatus", String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_haveSixCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = NotificationRepository.class.getDeclaredMethods();
        assertEquals(6, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = NotificationRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("Notification"));
        assertTrue(genericSignature.contains("UUID"));
    }

    @Test
    void should_declareFindByStatusAndRetryCountLessThanAndCreatedAtAfter_when_interfaceInspected() throws NoSuchMethodException {
        Method method = NotificationRepository.class.getMethod(
                "findByStatusAndRetryCountLessThanAndCreatedAtAfter", String.class, int.class, Instant.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(3, method.getParameterCount());
    }

    @Test
    void should_declareFindByStatusOrderByCreatedAtDesc_when_interfaceInspected() throws NoSuchMethodException {
        Method method = NotificationRepository.class.getMethod(
                "findByStatusOrderByCreatedAtDesc", String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }
}
