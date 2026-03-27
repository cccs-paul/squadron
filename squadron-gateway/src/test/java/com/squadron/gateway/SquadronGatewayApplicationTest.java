package com.squadron.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for SquadronGatewayApplication.
 */
class SquadronGatewayApplicationTest {

    @Test
    void should_haveMainMethod() {
        // Verify the main method exists and is callable (without actually starting Spring)
        assertDoesNotThrow(() -> {
            var method = SquadronGatewayApplication.class.getMethod("main", String[].class);
            assert method != null;
        });
    }

    @Test
    void should_beAnnotatedWithSpringBootApplication() {
        var annotation = SquadronGatewayApplication.class
                .getAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class);
        assert annotation != null : "Class should be annotated with @SpringBootApplication";
    }
}
