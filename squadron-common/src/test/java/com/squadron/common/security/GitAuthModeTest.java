package com.squadron.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitAuthModeTest {

    @Test
    void should_haveHttpsTokenValue_when_enumDefined() {
        assertEquals("HTTPS_TOKEN", GitAuthMode.HTTPS_TOKEN.name());
    }

    @Test
    void should_haveSshKeyValue_when_enumDefined() {
        assertEquals("SSH_KEY", GitAuthMode.SSH_KEY.name());
    }

    @Test
    void should_haveTwoValues_when_enumDefined() {
        GitAuthMode[] values = GitAuthMode.values();

        assertEquals(2, values.length);
    }

    @Test
    void should_returnCorrectEnum_when_valueOfCalled() {
        assertEquals(GitAuthMode.HTTPS_TOKEN, GitAuthMode.valueOf("HTTPS_TOKEN"));
        assertEquals(GitAuthMode.SSH_KEY, GitAuthMode.valueOf("SSH_KEY"));
    }

    @Test
    void should_throwIllegalArgumentException_when_invalidValueOfCalled() {
        assertThrows(IllegalArgumentException.class, () -> GitAuthMode.valueOf("UNKNOWN"));
    }

    @Test
    void should_haveCorrectOrdinalOrder_when_enumDefined() {
        assertTrue(GitAuthMode.HTTPS_TOKEN.ordinal() < GitAuthMode.SSH_KEY.ordinal());
    }
}
