package com.squadron.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class SquadronIdentityApplicationTest {

    @Test
    void should_haveSpringBootApplicationAnnotation_when_checked() {
        assertTrue(SquadronIdentityApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void should_haveEnableJpaRepositoriesAnnotation_when_checked() {
        assertTrue(SquadronIdentityApplication.class.isAnnotationPresent(EnableJpaRepositories.class));
    }

    @Test
    void should_notThrow_when_mainMethodCalled() {
        try (MockedStatic<SpringApplication> mockedSpringApp = mockStatic(SpringApplication.class)) {
            mockedSpringApp.when(() -> SpringApplication.run(SquadronIdentityApplication.class, new String[]{}))
                    .thenReturn(null);

            assertDoesNotThrow(() -> SquadronIdentityApplication.main(new String[]{}));

            mockedSpringApp.verify(() -> SpringApplication.run(SquadronIdentityApplication.class, new String[]{}));
        }
    }

    @Test
    void should_haveMainMethod_when_checked() throws NoSuchMethodException {
        var method = SquadronIdentityApplication.class.getDeclaredMethod("main", String[].class);

        assertNotNull(method);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void should_instantiate_when_constructed() {
        SquadronIdentityApplication app = new SquadronIdentityApplication();

        assertNotNull(app);
    }
}
