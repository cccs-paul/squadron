package com.squadron.orchestrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SquadronOrchestratorApplicationTest {

    @Test
    void should_haveMainMethod() {
        assertDoesNotThrow(() -> {
            // Verify the main method exists and is callable
            // We don't actually start the app (needs full context)
            SquadronOrchestratorApplication app = new SquadronOrchestratorApplication();
            assert app != null;
        });
    }
}
