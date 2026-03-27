package com.squadron.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SquadronPlatformApplicationTest {

    @Test
    void should_haveMainMethod() {
        assertDoesNotThrow(() -> {
            // Verify the main method exists and the class can be loaded
            SquadronPlatformApplication.class.getDeclaredMethod("main", String[].class);
        });
    }
}
