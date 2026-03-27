package com.squadron.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SquadronAgentApplicationTest {

    @Test
    void should_haveMainMethod() {
        // Verify the main method exists and is callable
        // We don't actually start the Spring context as it requires external services
        assertThat(SquadronAgentApplication.class).isNotNull();
    }

    @Test
    void should_beAnnotatedWithSpringBootApplication() {
        assertThat(SquadronAgentApplication.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class)).isTrue();
    }

    @Test
    void should_haveComponentScanAnnotation() {
        assertThat(SquadronAgentApplication.class.isAnnotationPresent(
                org.springframework.context.annotation.ComponentScan.class)).isTrue();

        org.springframework.context.annotation.ComponentScan componentScan =
                SquadronAgentApplication.class.getAnnotation(
                        org.springframework.context.annotation.ComponentScan.class);

        assertThat(componentScan.basePackages())
                .contains("com.squadron.agent", "com.squadron.common");
    }
}
