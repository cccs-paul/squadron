package com.squadron.review;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class SquadronReviewApplicationTest {

    @Test
    void should_haveSpringBootApplicationAnnotation_when_classInspected() {
        assertTrue(SquadronReviewApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void should_haveComponentScanAnnotation_when_classInspected() {
        assertTrue(SquadronReviewApplication.class.isAnnotationPresent(ComponentScan.class));
    }

    @Test
    void should_scanCorrectBasePackages_when_classInspected() {
        ComponentScan annotation = SquadronReviewApplication.class.getAnnotation(ComponentScan.class);
        String[] packages = annotation.basePackages();
        assertEquals(2, packages.length);
        assertEquals("com.squadron.review", packages[0]);
        assertEquals("com.squadron.common", packages[1]);
    }

    @Test
    void should_haveMainMethod_when_classInspected() throws NoSuchMethodException {
        var method = SquadronReviewApplication.class.getMethod("main", String[].class);
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void should_callSpringApplicationRun_when_mainInvoked() {
        try (MockedStatic<SpringApplication> springApp = mockStatic(SpringApplication.class)) {
            SquadronReviewApplication.main(new String[]{});
            springApp.verify(() -> SpringApplication.run(SquadronReviewApplication.class, new String[]{}));
        }
    }
}
