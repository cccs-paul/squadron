package com.squadron.workspace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class SquadronWorkspaceApplicationTest {

    @Test
    void should_haveSpringBootApplicationAnnotation_when_classInspected() {
        assertTrue(SquadronWorkspaceApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void should_haveEnableJpaRepositoriesAnnotation_when_classInspected() {
        assertTrue(SquadronWorkspaceApplication.class.isAnnotationPresent(EnableJpaRepositories.class));
    }

    @Test
    void should_scanCorrectBasePackages_when_classInspected() {
        SpringBootApplication annotation = SquadronWorkspaceApplication.class.getAnnotation(SpringBootApplication.class);
        String[] packages = annotation.scanBasePackages();
        assertEquals(2, packages.length);
        assertEquals("com.squadron.workspace", packages[0]);
        assertEquals("com.squadron.common", packages[1]);
    }

    @Test
    void should_haveMainMethod_when_classInspected() throws NoSuchMethodException {
        var method = SquadronWorkspaceApplication.class.getMethod("main", String[].class);
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void should_callSpringApplicationRun_when_mainInvoked() {
        try (MockedStatic<SpringApplication> springApp = mockStatic(SpringApplication.class)) {
            SquadronWorkspaceApplication.main(new String[]{});
            springApp.verify(() -> SpringApplication.run(SquadronWorkspaceApplication.class, new String[]{}));
        }
    }
}
