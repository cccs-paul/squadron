package com.squadron.git;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.MockedStatic;

class SquadronGitApplicationTest {

    @Test
    void should_haveMainMethod() throws NoSuchMethodException {
        assertNotNull(SquadronGitApplication.class.getMethod("main", String[].class));
    }

    @Test
    void should_beAnnotatedWithSpringBootApplication() {
        assertTrue(SquadronGitApplication.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class));
    }

    @Test
    void should_callSpringApplicationRun_when_mainInvoked() {
        try (MockedStatic<SpringApplication> springApp = mockStatic(SpringApplication.class)) {
            springApp.when(() -> SpringApplication.run(eq(SquadronGitApplication.class), any(String[].class)))
                    .thenReturn(null);

            SquadronGitApplication.main(new String[]{});

            springApp.verify(() -> SpringApplication.run(eq(SquadronGitApplication.class), any(String[].class)));
        }
    }

    @Test
    void should_passArgsToSpringApplication_when_mainInvoked() {
        String[] args = {"--server.port=9090", "--spring.profiles.active=test"};
        try (MockedStatic<SpringApplication> springApp = mockStatic(SpringApplication.class)) {
            springApp.when(() -> SpringApplication.run(eq(SquadronGitApplication.class), any(String[].class)))
                    .thenReturn(null);

            SquadronGitApplication.main(args);

            springApp.verify(() -> SpringApplication.run(SquadronGitApplication.class, args));
        }
    }
}
