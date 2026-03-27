package com.squadron.notification.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationPreferenceRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(NotificationPreferenceRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(NotificationPreferenceRepository.class.isInterface());
    }

    @Test
    void should_declareFindByUserId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = NotificationPreferenceRepository.class.getMethod("findByUserId", UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_haveOneCustomQueryMethod_when_interfaceInspected() {
        Method[] methods = NotificationPreferenceRepository.class.getDeclaredMethods();
        assertEquals(1, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = NotificationPreferenceRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("NotificationPreference"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
