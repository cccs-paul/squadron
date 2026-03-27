package com.squadron.notification;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class SquadronNotificationApplicationTest {

    @Test
    void should_haveSpringBootApplicationAnnotation_when_classInspected() {
        assertTrue(SquadronNotificationApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void should_scanCorrectBasePackages_when_classInspected() {
        SpringBootApplication annotation = SquadronNotificationApplication.class.getAnnotation(SpringBootApplication.class);
        String[] packages = annotation.scanBasePackages();
        assertEquals(2, packages.length);
        assertEquals("com.squadron.notification", packages[0]);
        assertEquals("com.squadron.common", packages[1]);
    }

    @Test
    void should_haveMainMethod_when_classInspected() throws NoSuchMethodException {
        var method = SquadronNotificationApplication.class.getMethod("main", String[].class);
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void should_callSpringApplicationRun_when_mainInvoked() {
        try (MockedStatic<SpringApplication> springApp = mockStatic(SpringApplication.class)) {
            SquadronNotificationApplication.main(new String[]{});
            springApp.verify(() -> SpringApplication.run(SquadronNotificationApplication.class, new String[]{}));
        }
    }
}
