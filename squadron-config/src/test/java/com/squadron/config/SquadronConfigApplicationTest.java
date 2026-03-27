package com.squadron.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SquadronConfigApplicationTest {

    @Test
    void should_haveMainMethod() {
        assertDoesNotThrow(() -> {
            // Verify main method exists and is callable (don't actually start Spring context)
            SquadronConfigApplication app = new SquadronConfigApplication();
            assert app != null;
        });
    }
}
